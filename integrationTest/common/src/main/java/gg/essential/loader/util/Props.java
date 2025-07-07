package gg.essential.loader.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Props {
    public static Properties readProps(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            return props;
        }
    }

    public static void writeProps(Path path, Properties props) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer out = Files.newBufferedWriter(path)) {
            props.store(out, null);
        }
    }

    public static Properties props(String...args) {
        Properties props = new Properties();
        for (String arg : args) {
            String[] split = arg.split("=", 2);
            props.put(split[0], split[1]);
        }
        return props;
    }
}
