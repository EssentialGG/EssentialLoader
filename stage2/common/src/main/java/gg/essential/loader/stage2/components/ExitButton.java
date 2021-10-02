package gg.essential.loader.stage2.components;

import gg.essential.loader.stage2.LoaderSwingUI;

import javax.swing.JButton;
import java.awt.*;

public class ExitButton extends JButton {

    public ExitButton() {
        this.setFocusPainted(false);
        this.setContentAreaFilled(false);
    }

    @Override
    protected void paintBorder(Graphics g) {
        final Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(model.isRollover() ? Color.white : LoaderSwingUI.COLOR_FOREGROUND);
        graphics.setStroke(new BasicStroke(1.7F));
        graphics.drawLine(0, 0, getWidth() - 1, getHeight() - 1);
        graphics.drawLine(0, getHeight() - 1, getWidth() - 1, 0);
        graphics.dispose();
    }
}
