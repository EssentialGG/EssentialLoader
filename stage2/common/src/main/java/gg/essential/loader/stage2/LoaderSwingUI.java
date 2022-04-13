package gg.essential.loader.stage2;

import gg.essential.loader.stage2.components.EssentialStyle;
import gg.essential.loader.stage2.components.EssentialProgressBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoaderSwingUI implements LoaderUI, EssentialStyle {
    private static final Rectangle PROGRESS_BOUNDS = new Rectangle(0, 0, 249, 35);

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
        // Make sure it's marked as complete
        this.progressBar.setValue(this.progressBar.getMaximum());
        // stay there for half a second
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // then close down
        this.frame.enableInputMethods(false);
        this.frame.dispose();
    }

    private void initFrame() {
        // Initialize the frame
        final JFrame frame = makeFrame(Window::dispose);

        // Initialize our standard content (vertical box layout with logo on top)
        final Container contentPane = makeContentWithLogo(frame);

        // Progress
        final JProgressBar progressBar = new JProgressBar();
        progressBar.setForeground(COLOR_PROGRESS_FILL);
        progressBar.setBackground(COLOR_OUTLINE);
        progressBar.setUI(new EssentialProgressBarUI());
        progressBar.setBorderPainted(false);
        progressBar.setBounds(PROGRESS_BOUNDS);

        final JLabel taskLabel = new JLabel("Updating...", SwingConstants.LEFT);
        taskLabel.setBorder(new EmptyBorder(0, 12, 0, 0));
        taskLabel.setForeground(Color.BLACK);
        taskLabel.setAlignmentX(Container.LEFT_ALIGNMENT);
        if (Fonts.semiBold != null) {
            taskLabel.setFont(Fonts.semiBold.deriveFont(15F));
        }
        taskLabel.setBounds(PROGRESS_BOUNDS);

        progressBar.getModel().addChangeListener(e -> {
            String label = progressBar.getValue() < progressBar.getMaximum() ? "Updating..." : "Completed.";
            if (!taskLabel.getText().equals(label)) {
                taskLabel.setText(label);
            }
        });

        final JLayeredPane progressBarLayers = new JLayeredPane();
        progressBarLayers.setPreferredSize(PROGRESS_BOUNDS.getSize());
        progressBarLayers.add(progressBar, 0, 0);
        progressBarLayers.add(taskLabel, 1, 0);

        final JPanel progressBarPanel = new JPanel(new GridLayout());
        progressBarPanel.setBackground(COLOR_BACKGROUND);
        progressBarPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        progressBarPanel.add(progressBarLayers);

        contentPane.add(progressBarPanel);

        frame.pack();
        frame.setVisible(true);

        this.frame = frame;
        this.progressBar = progressBar;
    }

    @SuppressWarnings("BusyWait")
    public static void main(String[] args) throws InterruptedException {
        LoaderSwingUI ui = new LoaderSwingUI();
        ui.start();
        ui.setDownloadSize(1000);
        while (ui.frame.isDisplayable()) {
            for (int i = 0; i < 1000; i += 4) {
                ui.setDownloaded(i);
                Thread.sleep(16);
            }
            ui.setDownloaded(1000);
            Thread.sleep(500);
        }
    }
}
