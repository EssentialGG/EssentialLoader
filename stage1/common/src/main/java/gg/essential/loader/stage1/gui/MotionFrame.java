package gg.essential.loader.stage1.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * When user dragged on this panel, the frame will move
 */
public class MotionFrame {
    public static void addMotion(final JFrame parent) {
        Point[] initialClick = {null};
    
        parent.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                initialClick[0] = e.getPoint();
                parent.getComponentAt(initialClick[0]);
            }
        });
    
        parent.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // get location of Window
                int thisX = parent.getLocation().x;
                int thisY = parent.getLocation().y;

                // Determine how much the mouse moved since the initial click
                int xMoved = (thisX + e.getX()) - (thisX + initialClick[0].x);
                int yMoved = (thisY + e.getY()) - (thisY + initialClick[0].y);

                // Move window to this position
                int X = thisX + xMoved;
                int Y = thisY + yMoved;
                parent.setLocation(X, Y);
            }
        });
    }
}