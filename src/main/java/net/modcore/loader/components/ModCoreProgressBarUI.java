package net.modcore.loader.components;

import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;

public class ModCoreProgressBarUI extends BasicProgressBarUI {
    private static final int STROKE_WIDTH = 3;

    @Override
    protected Dimension getPreferredInnerHorizontal() {
        return new Dimension(380, 20);
    }

    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Progress outline
        g2d.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(new Color(151, 151, 151));

        final int width = progressBar.getWidth();
        final int height = progressBar.getHeight();

        final int arcSize = height - STROKE_WIDTH;

        g2d.drawRoundRect(1, 1, width - STROKE_WIDTH - 1, height - STROKE_WIDTH, arcSize, arcSize);

        // Progress fill
        final double percent = progressBar.getPercentComplete();
        final int innerWidth = (int) Math.round(width * percent);

        g2d.setPaint(progressBar.getForeground());
        g2d.fillRoundRect(0, 0, innerWidth, height, height, height);

        g2d.dispose();
    }
}
