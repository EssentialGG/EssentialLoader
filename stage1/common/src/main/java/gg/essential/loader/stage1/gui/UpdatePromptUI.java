package gg.essential.loader.stage1.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static gg.essential.loader.stage1.gui.ButtonShadowBorder.X_SHADOW;
import static gg.essential.loader.stage1.gui.ButtonShadowBorder.Y_SHADOW;

public class UpdatePromptUI implements EssentialStyle {
    private final CompletableFuture<Boolean> closedFuture = new CompletableFuture<>();

    private final JFrame frame = makeFrame(it -> closedFuture.complete(null));

    private final String title;
    private final String description;

    public UpdatePromptUI(String title, String description) {
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
        buttons.add(makeButton("Skip Update", COLOR_BUTTON, COLOR_BUTTON_HOVER, () -> closedFuture.complete(false)));
        buttons.add(Box.createHorizontalStrut(12 - Y_SHADOW * 2));
        buttons.add(makeButton("Update Now", COLOR_PRIMARY_BUTTON, COLOR_PRIMARY_BUTTON_HOVER, () -> closedFuture.complete(true)));
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
        UpdatePromptUI ui = new UpdatePromptUI("Essential Loader Update!", "Short text of stuff we added in this fantastic update of ours, oh yeah, let's add more text.");
        // UpdatePromptUI ui = new UpdatePromptUI("Essential Mod Update!", "Perferendis sit alias totam eligendi debitis expedita. Voluptatibus dolores placeat in et velit labore itaque fugiat. Esse ullam unde quia ipsum autem. Omnis cumque dolore itaque animi qui et possimus optio. Delectus nemo ut amet delectus nesciunt rerum at eos. Itaque debitis perferendis molestiae quibusdam.");
        ui.show();
        System.out.println(ui.waitForClose());
    }
}
