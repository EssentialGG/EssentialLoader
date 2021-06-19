package gg.essential.loader.stage2.components;

import javax.swing.JButton;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

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
