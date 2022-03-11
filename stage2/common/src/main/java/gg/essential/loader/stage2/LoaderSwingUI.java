package gg.essential.loader.stage2;

import gg.essential.loader.stage2.components.EssentialStyle;
import gg.essential.loader.stage2.components.EssentialProgressBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoaderSwingUI implements LoaderUI, EssentialStyle {
    private static final int FRAME_WIDTH = 472, FRAME_HEIGHT = 261;

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
        // Initialize the frame
        final JFrame frame = makeFrame(FRAME_WIDTH, FRAME_HEIGHT, Window::dispose);

        // Initialize our standard content (vertical box layout with logo on top)
        final Container contentPane = makeContentWithLogo(frame);

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
        if (Fonts.bold != null) {
            taskLabel.setFont(Fonts.bold.deriveFont(14F));
        }

        taskLabelPanel.add(taskLabel, BorderLayout.CENTER);
        contentPane.add(taskLabelPanel);

        frame.setVisible(true);

        this.frame = frame;
        this.progressBar = progressBar;
    }
}
