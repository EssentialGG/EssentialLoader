package gg.essential.loader.stage2.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static gg.essential.loader.stage2.components.ButtonShadowBorder.X_SHADOW;
import static gg.essential.loader.stage2.components.ButtonShadowBorder.Y_SHADOW;

public class RestartUI implements EssentialStyle {
    private final CompletableFuture<Boolean> closedFuture = new CompletableFuture<>();

    private final JFrame frame = makeFrame(it -> closedFuture.complete(null));

    private final String title;
    private final String description;

    public RestartUI(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void show() {
        final List<JLabel> htmlLabels = new ArrayList<>();

        final JPanel content = makeContent(frame);
        content.setBorder(new EmptyBorder(0, 60 - X_SHADOW, 0, 60 - X_SHADOW));
        content.add(Box.createHorizontalStrut(CONTENT_WIDTH));

        htmlLabels.add(makeTitle(content, html(centered(title))));

        final JLabel explanation = new JLabel(html(centered(description)), SwingConstants.CENTER);
        explanation.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
        explanation.setForeground(COLOR_FOREGROUND);
        explanation.setAlignmentX(Container.CENTER_ALIGNMENT);
        if (Fonts.medium != null) {
            explanation.setFont(Fonts.medium.deriveFont(16F));
        }
        content.add(explanation);
        htmlLabels.add(explanation);

        content.add(Box.createVerticalStrut(32 - Y_SHADOW));

        final JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(makeButton("Restart", COLOR_PRIMARY_BUTTON, COLOR_BUTTON_HOVER, () -> closedFuture.complete(true)));
        content.add(buttons);

        content.add(Box.createVerticalStrut(32 - Y_SHADOW));

        frame.pack();

        htmlLabels.forEach(this::fixJLabelHeight);
        frame.pack();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public Boolean waitForClose() {
        Boolean verdict = closedFuture.join();
        close();
        return verdict;
    }

    public void close() {
        frame.dispose();
    }

    public static void main(String[] args) {
        RestartUI ui = new RestartUI("Restart required!", "A restart is required for something.");
        ui.show();
        System.out.println(ui.waitForClose());
    }
}
