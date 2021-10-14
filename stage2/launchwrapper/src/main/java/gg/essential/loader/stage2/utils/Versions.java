package gg.essential.loader.stage2.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static gg.essential.loader.stage2.EssentialLoader.LOGGER;
import static gg.essential.loader.stage2.EssentialLoaderBase.asJar;

public class Versions {
    public static int compare(String what, String left, String right) {
        return compare(parseVersion(what, left), parseVersion(what, right));
    }

    public static int compare(int[] left, int[] right) {
        if (left == null || right == null) {
            return 0;
        }
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l < r) {
                return -1;
            } else if (l > r) {
                return 1;
            }
        }
        return 0;
    }

    public static int[] parseVersion(String what, String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("[.-]");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                LOGGER.warn("Failed to parse {} version \"{}\".", what, version);
                LOGGER.debug(e);
                return null;
            }
        }
        return numbers;
    }

    public static String getMixinVersion(URL jarUrl) {
        try (FileSystem fileSystem = FileSystems.newFileSystem(asJar(jarUrl.toURI()), Collections.emptyMap())) {
            Path bootstrapPath = fileSystem.getPath("org", "spongepowered", "asm", "launch", "MixinBootstrap.class");
            try (InputStream inputStream = Files.newInputStream(bootstrapPath)) {
                ClassReader reader = new ClassReader(inputStream);
                ClassNode classNode = new ClassNode(Opcodes.ASM5);
                reader.accept(classNode, 0);
                for (FieldNode field : classNode.fields) {
                    if (field.name.equals("VERSION")) {
                        return String.valueOf(field.value);
                    }
                }
                LOGGER.warn("Failed to determine version of bundled mixin: no VERSION field in MixinBootstrap");
            }
        } catch (URISyntaxException | IOException e) {
            LOGGER.warn("Failed to determine version of bundled mixin:", e);
        }
        return null;
    }

    public static String getAsmVersion(URL jarUrl) {
        try (FileSystem fileSystem = FileSystems.newFileSystem(asJar(jarUrl.toURI()), Collections.emptyMap())) {
            // There is no nice way to get the version while we explode the ASM jar directly into our jar.
            // So we take an educated guess based on stuff we care about.
            Path asmPath = fileSystem.getPath("org", "objectweb", "asm");
            if (Files.exists(asmPath.resolve("commons").resolve("ClassRemapper.class"))) {
                return "5.2"; // default with 1.12.2, sufficient for Mixin 0.8
            } else if (Files.exists(asmPath.resolve("Opcodes.class"))) {
                return "5.0.3"; // default with 1.8.9, not sufficient for Mixin 0.8
            } else {
                return null;
            }
        } catch (URISyntaxException | IOException e) {
            LOGGER.warn("Failed to determine version of bundled asm:", e);
        }
        return null;
    }
}
