package gg.essential.loader.stage2.components;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;

public interface EssentialStyle {
    Color COLOR_BACKGROUND = new Color(27, 27, 28);
    Color COLOR_FOREGROUND = new Color(103, 103, 103);
    Color COLOR_OUTLINE = new Color(55, 55, 56);
    Color COLOR_PROGRESS_FILL = new Color(43, 197, 83);

    default JFrame makeFrame(int width, int height, Consumer<JFrame> onExit) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(NimbusLookAndFeel.class.getName());
        } catch (Exception ignored) {
        }

        // Initialize the frame
        final JFrame frame = new JFrame();
        frame.setTitle("Updating Essential...");
        try {
            frame.setIconImage(ImageIO.read(Objects.requireNonNull(getClass().getResource("/assets/essential-loader-stage2/icon.png"))));
        } catch (IOException e) {
            e.printStackTrace();
        }
        frame.setUndecorated(true);
        frame.setResizable(false);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit.accept(frame);
            }
        });

        MotionFrame.addMotion(frame);

        // Setting the background and the layout
        final Container container = frame.getContentPane();
        container.setBackground(COLOR_BACKGROUND);
        container.setLayout(null);

        // Exit button
        final ExitButton exit = new ExitButton();
        exit.setBounds(width - 36, 24, 10, 10);
        exit.setFocusPainted(false);
        exit.addActionListener(e -> onExit.accept(frame));
        container.add(exit);

        return frame;
    }

    default JPanel makeContentWithLogo(JFrame frame) {
        // Setting the background and the layout
        final JPanel contentPane = new JPanel();
        contentPane.setBackground(COLOR_BACKGROUND);
        contentPane.setBounds(new Rectangle(0, 0, frame.getWidth(), frame.getHeight()));
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        frame.getContentPane().add(contentPane);

        // Logo
        try {
            final Image icon = ImageIO.read(Objects.requireNonNull(getClass().getResource("/assets/essential-loader-stage2/essential.png")));
            final JLabel label = new JLabel(new ImageIcon(icon));
            label.setBorder(new EmptyBorder(42, 0, 0, 0));
            label.setAlignmentX(Container.CENTER_ALIGNMENT);
            contentPane.add(label);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return contentPane;
    }

    class Fonts {
        public static final Font bold = createFont("/assets/essential-loader-stage2/Gilroy-Bold.otf", Font.TRUETYPE_FONT);

        private static Font createFont(String path, int format) {
            try (InputStream stream = Fonts.class.getResourceAsStream(path)) {
                return Font.createFont(format, stream);
            } catch (IOException | FontFormatException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
