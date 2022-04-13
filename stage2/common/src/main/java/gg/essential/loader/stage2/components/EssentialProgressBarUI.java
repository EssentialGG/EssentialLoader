package gg.essential.loader.stage2.components;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class EssentialProgressBarUI extends BasicProgressBarUI {
    private static final int STROKE_WIDTH = 3;

    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        final int width = progressBar.getWidth();
        final int height = progressBar.getHeight();

        // Progress fill
        final double percent = progressBar.getPercentComplete();
        final int innerWidth = (int) Math.round(width * percent);

        g2d.setColor(progressBar.getBackground());
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(progressBar.getForeground());
        g2d.fillRect(0, 0, innerWidth, height);
        g2d.dispose();
    }
}
