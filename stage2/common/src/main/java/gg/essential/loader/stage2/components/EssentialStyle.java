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
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public interface EssentialStyle {
    Color COLOR_BACKGROUND = new Color(24, 24, 24);
    Color COLOR_FOREGROUND = new Color(152, 166, 174);
    Color COLOR_HIGHLIGHT = new Color(227, 245, 255);
    Color COLOR_OUTLINE = new Color(64, 64, 64);
    Color COLOR_PROGRESS_FILL = new Color(10, 130, 253);
    Color COLOR_BUTTON = new Color(102, 102, 102);
    Color COLOR_BUTTON_HOVER = COLOR_PROGRESS_FILL;

    default JFrame makeFrame(Consumer<JFrame> onExit) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(NimbusLookAndFeel.class.getName());
        } catch (Exception ignored) {
        }

        // Initialize the frame
        final JFrame frame = new JFrame();
        frame.setTitle("Updating Essential...");
        try {
            boolean isMacOS = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).startsWith("mac");
            String path = "/assets/essential-loader-stage2/icon" + (isMacOS ? ".macos" : "") + ".png";
            frame.setIconImage(ImageIO.read(Objects.requireNonNull(getClass().getResource(path))));
        } catch (IOException e) {
            e.printStackTrace();
        }
        frame.setUndecorated(true);
        frame.setResizable(false);

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

        return frame;
    }

    default JPanel makeContentWithLogo(JFrame frame) {
        // Setting the background and the layout
        final JPanel contentPane = new JPanel();
        contentPane.setBackground(COLOR_BACKGROUND);
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        frame.getContentPane().add(contentPane);
        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.PAGE_AXIS));

        // Logo
        try {
            final Image icon = ImageIO.read(Objects.requireNonNull(getClass().getResource("/assets/essential-loader-stage2/essential.png")));
            final JLabel label = new JLabel(new ImageIcon(icon));
            label.setBorder(new EmptyBorder(26, 0, 26, 0));
            label.setAlignmentX(Container.CENTER_ALIGNMENT);
            contentPane.add(label);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return contentPane;
    }

    default String withColor(Color color, String str) {
        String red = Integer.toHexString(color.getRed());
        String green = Integer.toHexString(color.getGreen());
        String blue = Integer.toHexString(color.getBlue());
        return "<font color=\"#" + red + green + blue + "\">" + str + "</font>";
    }

    class Fonts {
        public static final Font medium = createFont("/assets/essential-loader-stage2/Gilroy-Medium.otf", Font.TRUETYPE_FONT);
        public static final Font semiBold = createFont("/assets/essential-loader-stage2/Gilroy-SemiBold.otf", Font.TRUETYPE_FONT);

        private static Font createFont(String path, int format) {
            try (InputStream stream = Fonts.class.getResourceAsStream(path)) {
                Font font = Font.createFont(format, stream);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            } catch (IOException | FontFormatException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
