package net.modcore.loader;

import net.modcore.loader.components.CircleButton;
import net.modcore.loader.components.ModCoreProgressBarUI;
import net.modcore.loader.components.MotionPanel;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.LinkedHashSet;

public final class ModCoreLoader {
    private static final String VERSION_URL = "https://api.modcore.net/api/v1/versions";
    private static final String ARTIFACT_URL = "https://static.sk1er.club/repo/mods/modcore/%1$s/%2$s/ModCore-%1$s%%20(%2$s).jar";
    private static final String CLASS_NAME = "net.modcore.api.tweaker.ModCoreTweaker";
    private static final String FILE_NAME = "ModCore-%s (%s).jar";
    private static final int FRAME_WIDTH = 470;
    private static final int FRAME_HEIGHT = 240;
    private static final boolean UPDATE = "true".equals(System.getProperty("modcore.autoUpdate", "true"));
    private static final char[] hexCodes;

    static {
        hexCodes = "0123456789ABCDEF".toCharArray();
    }

    private final Color COLOR_BACKGROUND = new Color(33, 34, 38);
    private final Color COLOR_FOREGROUND = new Color(141, 141, 143);
    private final Color COLOR_TITLE_BACKGROUND = new Color(27, 28, 31);
    private final Color COLOR_PROGRESS_FILL = new Color(1, 165, 82);
    private final Color COLOR_EXIT = new Color(248, 203, 25);

    private final File gameDir;
    private final String gameVersion;

    private JFrame frame;
    private JProgressBar progressBar;

    public ModCoreLoader(final File gameDir, final String gameVersion) {
        this.gameDir = gameDir;
        this.gameVersion = gameVersion;
    }

    public static String toHex(final byte[] bytes) {
        final StringBuilder r = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            r.append(hexCodes[b >> 4 & 0xF]);
            r.append(hexCodes[b & 0xF]);
        }
        return r.toString();
    }

    public static byte[] checksum(final File input, final String name) {
        try (final InputStream in = new FileInputStream(input)) {
            final MessageDigest digest = MessageDigest.getInstance(name);
            final byte[] block = new byte[4096];
            int length;
            while ((length = in.read(block)) > 0) {
                digest.update(block, 0, length);
            }
            return digest.digest();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void load() {
        if (isInClassPath() && isInitialized()) {
            return;
        }

        final File dataDir = new File(gameDir, "modcore");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IllegalStateException("Unable to create necessary files");
        }

        final JsonHolder completeDocument = HttpUtils.fetchJson(VERSION_URL);
        final JsonHolder hashes = completeDocument.optJSONObject("hashes");
        final JsonHolder versions = completeDocument.optJSONObject("versions");
        if (!versions.has(gameVersion)) {
            System.out.println("Unsupported game version: " + gameVersion);
            return;
        }
        final String expectedHash = hashes.optString(gameVersion);
        final String remoteVersion = versions.optString(gameVersion);
        final boolean failed = versions.getKeys().size() == 0 || (versions.has("success") && !versions.optBoolean("success"));

        File modcoreFile = new File(dataDir, String.format(FILE_NAME, remoteVersion, gameVersion));

        if (!modcoreFile.exists() && !failed || (UPDATE && modcoreFile.exists() && !toHex(checksum(modcoreFile, "SHA-256")).equalsIgnoreCase(expectedHash))) {
            initFrame();
            File metaDataFile = new File(dataDir, "metadata.json");
            JsonHolder metaData = new JsonHolder();
            if (metaDataFile.exists()) {
                try {
                    metaData = new JsonHolder(FileUtils.readFileToString(metaDataFile));
                    if (metaData.has(gameVersion)) {
                        File oldJar = new File(dataDir, String.format(FILE_NAME, metaData.optString(gameVersion), gameVersion));
                        if (oldJar.exists()) {
                            if (UPDATE)
                                oldJar.delete();
                            else {
                                modcoreFile = oldJar;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if ((UPDATE || !modcoreFile.exists()) && downloadFile(String.format(ARTIFACT_URL, remoteVersion, gameVersion), modcoreFile, expectedHash)) {
                metaData.put(gameVersion, remoteVersion);
                try {
                    FileUtils.write(metaDataFile, metaData.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        addToClasspath(modcoreFile);

        if (!isInClassPath()) {
            throw new IllegalStateException("Something went wrong; ModCore is not found in the classpath. Exists? " + modcoreFile.exists());
        }

    }

    private void addToClasspath(final File file) {
        try {
            final URL url = file.toURI().toURL();
            Launch.classLoader.addURL(url);

            final ClassLoader classLoader = ModCoreLoader.class.getClassLoader();
            final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    private boolean isInitialized() {
        try {
            return net.modcore.api.tweaker.ModCoreTweaker.initialized;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isInClassPath() {
        try {
            LinkedHashSet<String> objects = new LinkedHashSet<>();
            objects.add(CLASS_NAME);
            Launch.classLoader.clearNegativeEntries(objects);
            Class.forName(CLASS_NAME);
            return true;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void initializeModCore() {
        try {
            net.modcore.api.tweaker.ModCoreTweaker.initialize(gameDir);
        } catch (Throwable e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    private boolean downloadFile(final String url, final File target, String expectedHash) {
        int attempts = 0;
        while (attempts < 5) {
            if (attemptDownload(url, target)) {
                String anotherString = toHex(checksum(target, "SHA-256"));
                if (expectedHash.equalsIgnoreCase(anotherString)) {
                    return true;
                } else {
                    System.out.println("ModCore hash does not match expected " + expectedHash + " " + anotherString);
                    if (target.exists()) {
                        FileUtils.deleteQuietly(target);
                    }
                }
            }
            attempts++;
        }
        JOptionPane.showConfirmDialog(null, "Unable to download required ModCore Library. If issues persist please contact support");
        return false;
    }

    private boolean attemptDownload(final String url, final File target) {
        try {
            final HttpURLConnection connection = HttpUtils.prepareConnection(url);
            final int contentLength = connection.getContentLength();

            try (final InputStream is = connection.getInputStream()) {
                try (FileOutputStream outputStream = new FileOutputStream(target)) {
                    final byte[] buffer = new byte[1024];
                    int read;

                    progressBar.setMaximum(contentLength);

                    while ((read = is.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, read);

                        final int progress = progressBar.getValue() + 1024;
                        progressBar.setValue(progress);
                    }
                    return true;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            frame.dispose();
        }
    }

    private void initFrame() {
        try {
            UIManager.setLookAndFeel(NimbusLookAndFeel.class.getName());
        } catch (Exception ignored) {
        }

        // Initialize the frame
        final JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(FRAME_WIDTH, FRAME_HEIGHT);
        frame.setResizable(false);

        frame.setShape(new RoundRectangle2D.Double(0, 0, FRAME_WIDTH, FRAME_HEIGHT, 16, 16));
        frame.setTitle("Updating Modcore...");

        // Setting the background and the layout
        final Container container = frame.getContentPane();
        container.setBackground(COLOR_BACKGROUND);
        container.setLayout(new BoxLayout(container, BoxLayout.PAGE_AXIS));

        // Title bar
        final MotionPanel titleBar = new MotionPanel(frame);
        titleBar.setLayout(null);
        titleBar.setBackground(COLOR_TITLE_BACKGROUND);
        titleBar.setBounds(0, 0, FRAME_WIDTH, 30);
        container.add(titleBar);

        final JLabel title = new JLabel("Updating Modcore...");
        title.setBounds(16, 16, 150, 16);
        title.setForeground(COLOR_FOREGROUND);
        titleBar.add(title, BorderLayout.LINE_START);

        final CircleButton exit = new CircleButton();
        exit.setBackground(COLOR_EXIT);
        exit.setForeground(COLOR_EXIT);
        exit.setBounds(FRAME_WIDTH - 32, 16, 16, 16);
        exit.setFocusPainted(false);
        titleBar.add(exit, BorderLayout.LINE_END);

        exit.addActionListener(e -> frame.dispose());

        // Logo
        try {
            final Image icon = ImageIO.read(getClass().getResource("/modcore.png"));
            final JLabel label = new JLabel(new ImageIcon(icon));
            label.setBorder(new EmptyBorder(35, 0, 0, 0));
            label.setAlignmentX(Container.CENTER_ALIGNMENT);
            container.add(label);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Progress
        final JProgressBar progressBar = new JProgressBar();
        progressBar.setForeground(COLOR_PROGRESS_FILL);
        progressBar.setBackground(COLOR_BACKGROUND);
        progressBar.setUI(new ModCoreProgressBarUI());
        progressBar.setBorderPainted(false);

        final JPanel panel = new JPanel();
        panel.setBackground(COLOR_BACKGROUND);
        panel.setBorder(new EmptyBorder(25, 0, 0, 0));
        panel.add(progressBar);

        container.add(panel);

        // Show the frame
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        frame.setLocation((int) (screenSize.getWidth() - FRAME_WIDTH) / 2, (int) (screenSize.getHeight() - FRAME_HEIGHT) / 2);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);

        this.frame = frame;
        this.progressBar = progressBar;
    }
}