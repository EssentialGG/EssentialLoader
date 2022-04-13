package gg.essential.loader.stage2.util;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Checksum {
    public static String getChecksum(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return DigestUtils.md5Hex(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
