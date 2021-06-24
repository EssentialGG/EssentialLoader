package gg.essential.loader.stage2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import gg.essential.loader.stage2.components.CircleButton;
import gg.essential.loader.stage2.components.EssentialProgressBarUI;
import gg.essential.loader.stage2.components.MotionPanel;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

public abstract class EssentialLoaderBase {

    private static final Logger LOGGER = LogManager.getLogger(EssentialLoaderBase.class);
    private static final String BASE_URL = System.getProperty(
        "essential.download.url",
        System.getenv().getOrDefault("ESSENTIAL_DOWNLOAD_URL", "https://downloads.essential.gg")
    );
    private static final String BRANCH = System.getProperty(
        "essential.branch",
        System.getenv().getOrDefault("ESSENTIAL_BRANCH", "stable")
    );
    private static final String VERSION_URL = BASE_URL + "/v1/mods/essential/essential/updates/" + BRANCH + "/%s/";
    protected static final String CLASS_NAME = "gg.essential.api.tweaker.EssentialTweaker";
    private static final String FILE_NAME = "Essential (%s).jar";
    private static final int FRAME_WIDTH = 470, FRAME_HEIGHT = 240;
    private static final boolean AUTO_UPDATE = "true".equals(System.getProperty("essential.autoUpdate", "true"));

    private final Color
        COLOR_BACKGROUND = new Color(33, 34, 38),
        COLOR_FOREGROUND = new Color(141, 141, 143),
        COLOR_TITLE_BACKGROUND = new Color(27, 28, 31),
        COLOR_PROGRESS_FILL = new Color(1, 165, 82),
        COLOR_EXIT = new Color(248, 203, 25);

    private final File gameDir;
    private final String gameVersion;

    private JFrame frame;
    private JProgressBar progressBar;

    public EssentialLoaderBase(final Path gameDir, final String gameVersion) {
        this.gameDir = gameDir.toFile();
        this.gameVersion = gameVersion;
    }

    public void load() {
        if (this.isInClassPath() && this.isInitialized()) {
            return;
        }

        final File dataDir = new File(this.gameDir, "essential");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IllegalStateException("Unable to create essential directory, no permissions?");
        }

        JsonObject responseObject;
        try {
            final HttpURLConnection httpURLConnection = this.prepareConnection(
                String.format(VERSION_URL, this.gameVersion.replace(".", "-"))
            );

            String response;
            try (final InputStream inputStream = httpURLConnection.getInputStream()) {
                response = IOUtils.toString(inputStream, Charset.defaultCharset());
            }

            JsonElement jsonElement = new JsonParser().parse(response);
            responseObject = jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;
        } catch (final IOException | JsonParseException e) {
            LOGGER.error("Error occurred when verifying game version {}.", this.gameVersion, e);
            return;
        }

        if (responseObject == null) {
            LOGGER.warn("Essential does not support the following game version: {}", this.gameVersion);
            return;
        }

        final JsonElement
            jsonUrl = responseObject.get("url"),
            jsonChecksum = responseObject.get("checksum");
        final String
            url = jsonUrl != null && jsonUrl.isJsonPrimitive() ? jsonUrl.getAsString() : null,
            checksum = jsonChecksum != null && jsonChecksum.isJsonPrimitive() ? responseObject.get("checksum").getAsString() : null;

        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(checksum)) {
            LOGGER.warn("Unexpected response object data (url={}, checksum={})", jsonUrl, jsonChecksum);
            return;
        }

        final File essentialFile = new File(dataDir, String.format(FILE_NAME, this.gameVersion));

        if (
            !essentialFile.exists() ||
            (AUTO_UPDATE && essentialFile.exists() && !checksum.equals(this.getChecksum(essentialFile)))
        ) {
            this.initFrame();

            if (AUTO_UPDATE) {
                if (essentialFile.exists()) {
                    essentialFile.delete();
                }

                if (!this.downloadFile(url, essentialFile, checksum)) {
                    return;
                }
            }
        }

        this.addToClasspath(essentialFile);

        if (!this.isInClassPath()) {
            throw new IllegalStateException("Could not find Essential in the classpath even though we added it without errors (fileExists=" + essentialFile.exists() + ").");
        }
    }

    private String getChecksum(final File input) {
        try (final InputStream inputStream = new FileInputStream(input)) {
            return DigestUtils.md5Hex(inputStream);
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private HttpURLConnection prepareConnection(final String url) throws IOException {
        final HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(url).openConnection();

        httpURLConnection.setRequestMethod("GET");
        httpURLConnection.setUseCaches(true);
        httpURLConnection.setReadTimeout(3000);
        httpURLConnection.setReadTimeout(3000);
        httpURLConnection.setDoOutput(true);

        httpURLConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (Essential Initializer)");

        return httpURLConnection;
    }

    protected abstract void addToClasspath(final File file);

    private boolean isInitialized() {
        try {
            return Class.forName(CLASS_NAME)
                .getField("initialized")
                .getBoolean(null);
        } catch (Throwable ignored) {
        }
        return false;
    }

    protected abstract boolean isInClassPath();

    public void initialize() {
        try {
            Class.forName(CLASS_NAME)
                .getDeclaredMethod("initialize", File.class)
                .invoke(null, gameDir);
        } catch (Throwable e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    private boolean downloadFile(final String url, final File target, String expectedHash) {
        if (!this.attemptDownload(url, target)) {
            LOGGER.warn("Unable to download Essential, please check your internet connection. If the problem persists, please contact Support.");
            return false;
        }

        final String downloadedChecksum = this.getChecksum(target);

        if (downloadedChecksum.equals(expectedHash)) {
            return true;
        }

        LOGGER.warn(
            "Downloaded Essential file checksum did not match what we expected (downloaded={}, expected={}",
            downloadedChecksum, expectedHash
        );

        // Do not keep the file they downloaded if validation failed.
        if (target.exists()) {
            target.delete();
        }

        return false;
    }

    private boolean attemptDownload(final String url, final File target) {
        try {
            final HttpURLConnection httpURLConnection = this.prepareConnection(url);
            final int contentLength = httpURLConnection.getContentLength();
            this.progressBar.setMaximum(contentLength);

            try (
                final InputStream inputStream = httpURLConnection.getInputStream();
                final FileOutputStream fileOutputStream = new FileOutputStream(target)
            ) {
                final byte[] buffer = new byte[1024];

                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    fileOutputStream.write(buffer, 0, read);
                    this.progressBar.setValue((this.progressBar.getValue()) + 1024);
                }

                return true;
            }
        } catch (final IOException e) {
            LOGGER.error("Error occurred when downloading file '{}'.", url, e);
            return false;
        } finally {
            this.frame.dispose();
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
        frame.setTitle("Updating Essential...");

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

        final JLabel title = new JLabel("Updating Essential...");
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
            final Image icon = ImageIO.read(Objects.requireNonNull(getClass().getResource("/essential.png")));
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
        progressBar.setUI(new EssentialProgressBarUI());
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