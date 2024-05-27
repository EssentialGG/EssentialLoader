package gg.essential.loader.stage1.gui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public interface EssentialStyle {
    String ASSETS_BASE = "/assets/essential-loader-stage1";

    Color COLOR_BACKGROUND = new Color(0x181818);
    Color COLOR_FOREGROUND = new Color(0x999999);
    Color COLOR_HIGHLIGHT = new Color(0xe5e5e5);
    Color COLOR_TITLE = new Color(0x1d6aff);
    Color COLOR_OUTLINE = new Color(0x323232);
    Color COLOR_PROGRESS_FILL = new Color(0x1d6aff);
    Color COLOR_BUTTON = new Color(0x323232);
    Color COLOR_BUTTON_HOVER = new Color(0x474747);
    Color COLOR_PRIMARY_BUTTON = new Color(0x1d6aff);
    Color COLOR_PRIMARY_BUTTON_HOVER = new Color(0x4a88ff);

    int CONTENT_WIDTH = 281;

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
            String path = ASSETS_BASE + "/icon" + (isMacOS ? ".macos" : "") + ".png";
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

    default JPanel makeContent(JFrame frame) {
        // Setting the background and the layout
        final JPanel contentPane = new JPanel();
        contentPane.setBackground(COLOR_BACKGROUND);
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        frame.getContentPane().add(contentPane);
        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.PAGE_AXIS));
        return contentPane;
    }

    default JPanel makeContentWithLogo(JFrame frame) {
        JPanel contentPane = makeContent(frame);

        // Logo
        try {
            final Image icon = ImageIO.read(Objects.requireNonNull(getClass().getResource(ASSETS_BASE + "/essential.png")));
            final JLabel label = new JLabel(new ImageIcon(icon));
            label.setBorder(new EmptyBorder(43, 0, 43, 0));
            label.setAlignmentX(Container.CENTER_ALIGNMENT);
            contentPane.add(label);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return contentPane;
    }

    default JLabel makeTitle(JPanel contentPane, String title) {
        final JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setForeground(COLOR_TITLE);
        if (Fonts.bold != null) {
            label.setFont(Fonts.bold.deriveFont(20F));
        }
        label.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
        label.setBorder(new EmptyBorder(32, 0, 16, 0));
        label.setAlignmentX(Container.CENTER_ALIGNMENT);
        contentPane.add(label);
        return label;
    }

    default JComponent makeButton(String text, Color color, Color colorHovered, Runnable onClick) {
        final JButton close = new JButton(text);
        Dimension size = new Dimension(144, 35);
        close.setMinimumSize(size);
        close.setMaximumSize(size);
        close.setPreferredSize(size);
        close.setFocusPainted(false);
        close.setContentAreaFilled(false);
        close.setForeground(COLOR_HIGHLIGHT);
        if (Fonts.semiBold != null) {
            close.setFont(Fonts.semiBold.deriveFont(16F));
        }
        close.addActionListener(e -> onClick.run());

        final JPanel closeContainer = new JPanel();
        closeContainer.setLayout(new BoxLayout(closeContainer, BoxLayout.X_AXIS));
        closeContainer.setAlignmentX(Component.CENTER_ALIGNMENT);
        closeContainer.setAlignmentY(Component.CENTER_ALIGNMENT);
        closeContainer.setBackground(color);
        close.getModel().addChangeListener(e ->
            closeContainer.setBackground(close.getModel().isRollover() ? colorHovered : color));
        closeContainer.add(close);

        return ButtonShadowBorder.create(closeContainer);
    }

    default String withColor(Color color, String str) {
        String red = Integer.toHexString(color.getRed());
        String green = Integer.toHexString(color.getGreen());
        String blue = Integer.toHexString(color.getBlue());
        return "<font color=\"#" + red + green + blue + "\">" + str + "</font>";
    }

    default String centered(String str) {
        return "<div style='text-align: center;'>" + str + "</div>";
    }

    default String html(String str) {
        return "<html>" + str + "</html>";
    }

    // For some reason, JLabels with html content, even though they render correctly, act as if they have the wrong size
    // (as if there were no line breaks) for layout purposes. This causes the window to be way too wide and not high
    // enough.
    // Found this workaround at the bottom of a SO thread after a lot of searching: https://stackoverflow.com/a/13509163
    default void fixJLabelHeight(JLabel label) {
        View view = (View) label.getClientProperty(BasicHTML.propertyKey);
        view.setSize(label.getWidth(), 0f);
        label.setSize((int) view.getPreferredSpan(View.X_AXIS), (int) view.getPreferredSpan(View.Y_AXIS));
    }

    class Fonts {
        public static final Font medium = createFont("/Gilroy-Medium.otf", Font.TRUETYPE_FONT);
        public static final Font mediumItalic = createFont("/Gilroy-Medium-Italic.otf", Font.TRUETYPE_FONT);
        public static final Font semiBold = createFont("/Gilroy-SemiBold.otf", Font.TRUETYPE_FONT);
        public static final Font bold = createFont("/Gilroy-Bold.otf", Font.TRUETYPE_FONT);

        private static Font createFont(String path, int format) {
            try (InputStream stream = Fonts.class.getResourceAsStream(ASSETS_BASE + path)) {
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
