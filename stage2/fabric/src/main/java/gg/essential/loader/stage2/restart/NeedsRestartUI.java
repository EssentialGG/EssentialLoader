package gg.essential.loader.stage2.restart;

import gg.essential.loader.stage2.components.EssentialStyle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NeedsRestartUI implements EssentialStyle {
    private static final int FRAME_WIDTH = 269;
    private static final int TEXT_HEIGHT = 74;

    private final CompletableFuture<?> closedFuture = new CompletableFuture<>();

    private final JFrame frame = makeFrame(it -> closedFuture.complete(null));

    private final List<String> updatedModNames;

    public NeedsRestartUI(List<String> updatedModNames) {
        this.updatedModNames = updatedModNames;
    }

    public void show() {
        final List<JLabel> htmlLabels = new ArrayList<>();

        final JPanel content = makeContentWithLogo(frame);
        content.setMinimumSize(new Dimension(FRAME_WIDTH, 0));
        content.setMaximumSize(new Dimension(FRAME_WIDTH, Integer.MAX_VALUE));
        content.setBorder(new EmptyBorder(0, 26, 0, 26));

        final JLabel explanation = new JLabel("<html>You have to <font color=white>restart the game to continue</font>. Updates from the following mods require a manual restart:</html>", SwingConstants.LEFT);
        explanation.setMaximumSize(new Dimension(FRAME_WIDTH, Integer.MAX_VALUE));
        explanation.setForeground(COLOR_FOREGROUND);
        explanation.setAlignmentX(Container.CENTER_ALIGNMENT);
        if (Fonts.medium != null) {
            explanation.setFont(Fonts.medium.deriveFont(15F));
        }
        content.add(explanation);
        htmlLabels.add(explanation);

        content.add(Box.createVerticalStrut(20));

        final JPanel modList = new JPanel();
        modList.setMaximumSize(new Dimension(FRAME_WIDTH, Integer.MAX_VALUE));
        modList.setBackground(COLOR_BACKGROUND);
        modList.setLayout(new BoxLayout(modList, BoxLayout.Y_AXIS));
        content.add(modList);

        for (String modName : updatedModNames) {
            final JPanel modPanel = new JPanel();
            modPanel.setLayout(new BorderLayout());
            modPanel.setBackground(COLOR_BACKGROUND);
            modList.add(modPanel);

            final JPanel bulletContainer = new JPanel();
            bulletContainer.setLayout(new BoxLayout(bulletContainer, BoxLayout.X_AXIS));
            bulletContainer.setBackground(COLOR_BACKGROUND);
            modPanel.add(bulletContainer, BorderLayout.LINE_START);

            final JComponent bullet = new JComponent() {
                @Override
                public boolean isOpaque() {
                    return false;
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics graphics = g.create();
                    if (graphics instanceof Graphics2D) {
                        ((Graphics2D) graphics).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    }
                    graphics.setColor(getForeground());
                    graphics.fillOval(0, 7, 4, 4);
                }
            };
            bullet.setMinimumSize(new Dimension(4, 15));
            bullet.setPreferredSize(new Dimension(4, 15));
            bullet.setMaximumSize(new Dimension(4, 15));
            bullet.setForeground(Color.WHITE);
            bullet.setAlignmentY(Component.TOP_ALIGNMENT);
            bulletContainer.add(Box.createHorizontalStrut(8));
            bulletContainer.add(bullet);
            bulletContainer.add(Box.createHorizontalStrut(8));

            final JLabel text = new JLabel("<html>" + modName + "</html>", SwingConstants.LEFT);
            text.setForeground(Color.WHITE);
            text.setAlignmentX(Container.LEFT_ALIGNMENT);
            if (Fonts.medium != null) {
                text.setFont(Fonts.medium.deriveFont(15F));
            }
            modPanel.add(text);
            htmlLabels.add(text);
        }

        content.add(Box.createVerticalStrut(26));

        final JButton close = new JButton("Quit Game");
        close.setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
        close.setFocusPainted(false);
        close.setContentAreaFilled(false);
        close.setForeground(Color.WHITE);
        if (Fonts.semiBold != null) {
            close.setFont(Fonts.semiBold.deriveFont(15F));
        }
        close.addActionListener(e -> closedFuture.complete(null));

        final JPanel closeContainer = new JPanel();
        closeContainer.setLayout(new BoxLayout(closeContainer, BoxLayout.X_AXIS));
        closeContainer.setAlignmentX(Component.CENTER_ALIGNMENT);
        closeContainer.setBackground(COLOR_BUTTON);
        close.getModel().addChangeListener(e ->
            closeContainer.setBackground(close.getModel().isRollover() ? COLOR_BUTTON_HOVER : COLOR_BUTTON));
        closeContainer.add(close);
        content.add(closeContainer);

        content.add(Box.createVerticalStrut(20));

        frame.pack();

        htmlLabels.forEach(this::fixJLabelHeight);
        frame.pack();

        frame.setVisible(true);
    }

    // For some reason, JLabels with html content, even though they render correctly, act as if they have the wrong size
    // (as if there were no line breaks) for layout purposes. This causes the window to be way to wide and not high
    // enough.
    // Found this workaround at the bottom of a SO thread after a lot of searching: https://stackoverflow.com/a/13509163
    private void fixJLabelHeight(JLabel label) {
        View view = (View) label.getClientProperty(BasicHTML.propertyKey);
        view.setSize(label.getWidth(), 0f);
        label.setSize((int) view.getPreferredSpan(View.X_AXIS), (int) view.getPreferredSpan(View.Y_AXIS));
    }

    public void waitForClose() {
        closedFuture.join();
        close();
    }

    public void close() {
        frame.dispose();
    }

    public static void main(String[] args) {
        // List<String> mods = Arrays.asList("Fabric Language Kotlin", "Vigilance");
        // List<String> mods = Arrays.asList("Fabric Language Kotlin", "Elementa", "Vigilance");
        List<String> mods = Arrays.asList("Fabric Language Kotlin", "Elementa", "Example Mod With Unreasonably Long Name", "Vigilance");
        NeedsRestartUI ui = new NeedsRestartUI(mods);
        ui.show();
        ui.waitForClose();
    }
}
