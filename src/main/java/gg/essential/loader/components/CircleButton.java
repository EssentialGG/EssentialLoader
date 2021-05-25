package gg.essential.loader.components;

import javax.swing.*;
import java.awt.*;

public class CircleButton extends JButton {

    public CircleButton() {
        this.setFocusPainted(false);
        this.setContentAreaFilled(false);
    }

    @Override
    protected void paintBorder(Graphics g) {
        final Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(this.getForeground());
        graphics.fillOval(0, 0, this.getWidth(), this.getHeight());
        graphics.dispose();
    }
}
