package gg.essential.loader.stage2;

import gg.essential.loader.stage2.components.ExitButton;
import gg.essential.loader.stage2.components.EssentialProgressBarUI;
import gg.essential.loader.stage2.components.MotionFrame;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class LoaderSwingUI implements LoaderUI {
    private static final int FRAME_WIDTH = 472, FRAME_HEIGHT = 261;
    public static final Color
        COLOR_BACKGROUND = new Color(27, 27, 28),
        COLOR_FOREGROUND = new Color(103, 103, 103),
        COLOR_OUTLINE = new Color(55, 55, 56),
        COLOR_PROGRESS_FILL = new Color(43, 197, 83),
        COLOR_EXIT = new Color(248, 203, 25);

    private JFrame frame;
    private JProgressBar progressBar;

    @Override
    public void start() {
        initFrame();
    }

    @Override
    public void setDownloadSize(int bytes) {
        this.progressBar.setMaximum(bytes);
    }

    @Override
    public void setDownloaded(int bytes) {
        this.progressBar.setValue(bytes);
    }

    @Override
    public void complete() {
        this.frame.enableInputMethods(false);
        this.frame.dispose();
    }

    private void initFrame() {
        try {
            UIManager.setLookAndFeel(NimbusLookAndFeel.class.getName());
        } catch (Exception ignored) {
        }
        
        // Fonts
        Font bold = createFont("/assets/essential-loader-stage2/Gilroy-Bold.otf", Font.TRUETYPE_FONT);

        // Initialize the frame
        final JFrame frame = new JFrame();
        try {
            frame.setIconImage(ImageIO.read(getClass().getResource("/assets/essential-loader-stage2/icon.png")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        frame.setUndecorated(true);
        frame.setSize(FRAME_WIDTH, FRAME_HEIGHT);
        frame.setResizable(false);

        frame.setTitle("Updating Essential...");
    
        MotionFrame.addMotion(frame);

        // Setting the background and the layout
        final Container container = frame.getContentPane();
        container.setBackground(COLOR_BACKGROUND);
        container.setLayout(null);

        // Exit button
        final ExitButton exit = new ExitButton();
        exit.setBounds(FRAME_WIDTH - 36, 24, 10, 10);
        exit.setFocusPainted(false);
        container.add(exit);
    
        exit.addActionListener(e -> frame.dispose());
    
        // Setting the background and the layout
        final Container contentPane = new JPanel();
        contentPane.setBackground(COLOR_BACKGROUND);
        contentPane.setBounds(new Rectangle(0, 0, FRAME_WIDTH, FRAME_HEIGHT));
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        container.add(contentPane);

        // Logo
        try {
            final Image icon = ImageIO.read(Objects.requireNonNull(getClass().getResource("/assets/essential-loader-stage2/essential.png")));
            final JLabel label = new JLabel(new ImageIcon(icon));
            label.setBorder(new EmptyBorder(42, 0, 0, 0));
            label.setAlignmentX(Container.CENTER_ALIGNMENT);
            contentPane.add(label);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Progress
        final JProgressBar progressBar = new JProgressBar();
        progressBar.setForeground(COLOR_PROGRESS_FILL);
        progressBar.setBackground(COLOR_OUTLINE);
        progressBar.setUI(new EssentialProgressBarUI());
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(340, 46));

        final JPanel progressBarPanel = new JPanel();
        progressBarPanel.setBackground(COLOR_BACKGROUND);
        progressBarPanel.setBorder(new EmptyBorder(30, 0, 0, 0));
        progressBarPanel.add(progressBar);

        contentPane.add(progressBarPanel);

        final JPanel taskLabelPanel = new JPanel();
        taskLabelPanel.setBackground(COLOR_BACKGROUND);
        taskLabelPanel.setLayout(new BorderLayout());

        final JLabel taskLabel = new JLabel("Updating Essential...", SwingConstants.CENTER);
        taskLabel.setForeground(COLOR_FOREGROUND);
        taskLabel.setAlignmentX(Container.CENTER_ALIGNMENT);
        if (bold != null) {
            taskLabel.setFont(bold.deriveFont(14F));
        }

        taskLabelPanel.add(taskLabel, BorderLayout.CENTER);
        contentPane.add(taskLabelPanel);

        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);

        this.frame = frame;
        this.progressBar = progressBar;
    }

    private Font createFont(String path, int format) {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            return Font.createFont(format, stream);
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
            return null;
        }
    }
}
