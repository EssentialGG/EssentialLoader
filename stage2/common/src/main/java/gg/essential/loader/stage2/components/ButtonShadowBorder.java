package gg.essential.loader.stage2.components;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;

public class ButtonShadowBorder extends AbstractBorder {
    public static final int X_SHADOW = 3;
    public static final int Y_SHADOW = 3;

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.right = X_SHADOW;
        insets.top = insets.bottom = Y_SHADOW;
        return insets;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(Color.BLACK);

        // Vertical shadow
        g.fillRect(x + width - X_SHADOW, y + Y_SHADOW * 2, X_SHADOW, height - 2 * Y_SHADOW);
        // Horizontal shadow
        g.fillRect(x + X_SHADOW * 2, y + height - Y_SHADOW, width - 2 * X_SHADOW, Y_SHADOW);
    }

    public static JPanel create(JPanel button) {
        final JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
        wrapper.setBorder(new ButtonShadowBorder());
        wrapper.add(button);
        return wrapper;
    }
}
