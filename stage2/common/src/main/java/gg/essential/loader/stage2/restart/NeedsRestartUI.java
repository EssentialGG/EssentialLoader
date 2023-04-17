package gg.essential.loader.stage2.restart;

import gg.essential.loader.stage2.components.EssentialStyle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static gg.essential.loader.stage2.components.ButtonShadowBorder.Y_SHADOW;

public class NeedsRestartUI implements EssentialStyle {
    private final CompletableFuture<?> closedFuture = new CompletableFuture<>();

    private final JFrame frame = makeFrame(it -> closedFuture.complete(null));

    private final List<String> updatedModNames;

    public NeedsRestartUI(List<String> updatedModNames) {
        this.updatedModNames = updatedModNames;
    }

    public void show() {
        final List<JLabel> htmlLabels = new ArrayList<>();

        final JPanel content = makeContent(frame);
        content.setBorder(new EmptyBorder(0, 60, 0, 60));
        content.add(Box.createHorizontalStrut(CONTENT_WIDTH));

        htmlLabels.add(makeTitle(content, html(centered("Essential requires<br>you to restart your game."))));

        final JLabel explanation = new JLabel(html(centered("Updates from the following mods<br>require a manual restart:")), SwingConstants.CENTER);
        explanation.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
        explanation.setForeground(COLOR_FOREGROUND);
        explanation.setAlignmentX(Container.CENTER_ALIGNMENT);
        if (Fonts.medium != null) {
            explanation.setFont(Fonts.medium.deriveFont(16F));
        }
        content.add(explanation);
        htmlLabels.add(explanation);

        content.add(Box.createVerticalStrut(19));

        final JPanel modList = new JPanel();
        modList.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
        modList.setBackground(COLOR_BACKGROUND);
        modList.setLayout(new BoxLayout(modList, BoxLayout.Y_AXIS));
        content.add(modList);

        for (String modName : updatedModNames) {
            final JLabel text = new JLabel(html(centered(modName)), SwingConstants.CENTER);
            text.setForeground(COLOR_HIGHLIGHT);
            text.setAlignmentX(Container.CENTER_ALIGNMENT);
            if (Fonts.mediumItalic != null) {
                text.setFont(Fonts.mediumItalic.deriveFont(16F));
            }
            modList.add(text);
            htmlLabels.add(text);
        }

        content.add(Box.createVerticalStrut(32 - Y_SHADOW));

        JComponent quitGame = makeButton("Quit Game", COLOR_BUTTON, COLOR_BUTTON_HOVER, () -> closedFuture.complete(null));
        content.add(quitGame);

        content.add(Box.createVerticalStrut(32 - Y_SHADOW));

        frame.pack();

        htmlLabels.forEach(this::fixJLabelHeight);
        frame.pack();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
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
