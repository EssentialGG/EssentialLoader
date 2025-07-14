package gg.essential.loader.stage2.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class MixinExtrasExtractor {
    private static final Logger LOGGER = LogManager.getLogger(MixinExtrasExtractor.class);

    private static final String MIXINEXTRAS_PACKAGE_PATH = "com/llamalad7/mixinextras";
    private static final String MIXIN_EXTRAS_VERSION_CLASS = MIXINEXTRAS_PACKAGE_PATH + "/service/MixinExtrasVersion.class";

    public static String readMixinExtrasVersion(Path jar, FileSystem jarFileSystem) {
        try {
            Path classFilePath = jarFileSystem.getPath(MIXIN_EXTRAS_VERSION_CLASS);
            if (!Files.exists(classFilePath)) return null;

            byte[] bytes = Files.readAllBytes(classFilePath);
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            FieldNode lastEnumField = null;
            for (FieldNode field : classNode.fields) {
                if ((field.access & Opcodes.ACC_ENUM) != 0) {
                    lastEnumField = field;
                }
            }
            if (lastEnumField == null) throw new UnsupportedOperationException("Failed to find any enum entries");

            MethodNode clinit = null;
            for (MethodNode method : classNode.methods) {
                if (method.name.equals("<clinit>")) {
                    clinit = method;
                    break;
                }
            }
            if (clinit == null) throw new UnsupportedOperationException("Failed to find static initializer");

            AbstractInsnNode insn = clinit.instructions.getFirst();

            // Search for field assignment
            while (insn != null) {
                if (insn instanceof FieldInsnNode && ((FieldInsnNode) insn).name.equals(lastEnumField.name)) {
                    break;
                }
                insn = insn.getNext();
            }
            if (insn == null) throw new UnsupportedOperationException("Failed to find enum field initializer");

            // Search backwards for the version string
            while (insn != null) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                    return (String) ((LdcInsnNode) insn).cst;
                }
                insn = insn.getPrevious();
            }

            throw new UnsupportedOperationException("Failed to find version argument");
        } catch (Exception e) {
            LOGGER.error("Failed to determine version of MixinExtras in {}", jar, e);
            return null;
        }
    }

    public static void extractMixinExtras(Path sourceJar, Path extractedJar, String version) throws IOException {
        // Create manifest file
        // One is implicitly required by LaunchClassLoader, otherwise won't be declaring `Package`s for the classes in
        // the jar, and MixinExtras initialization code will consequently NPE.
        Manifest manifest = new Manifest();
        // and while we're at it, may as well set the correct version
        manifest.getMainAttributes().putValue("Implementation-Version", version);

        // Create empty jar file
        try (OutputStream out = Files.newOutputStream(extractedJar, StandardOpenOption.TRUNCATE_EXISTING)) {
            new JarOutputStream(out, manifest).close();
        }

        // Copy MixinExtras package from source jar to our new jar
        try (FileSystem srcFs = FileSystems.newFileSystem(sourceJar, (ClassLoader) null)) {
            try (FileSystem dstFs = FileSystems.newFileSystem(extractedJar, (ClassLoader) null)) {
                // Note: These are `toAbsolutePath`ed as a workaround for ZipFileSystem sometimes returning absolute
                //       `Path`s from `walk` even when the `start` `Path` is relative.
                Path srcRoot = srcFs.getPath(MIXINEXTRAS_PACKAGE_PATH).toAbsolutePath();
                Path dstRoot = dstFs.getPath(MIXINEXTRAS_PACKAGE_PATH).toAbsolutePath();

                try (Stream<Path> stream = Files.walk(srcRoot)) {
                    for (Path src : (Iterable<? extends Path>) stream::iterator) {
                        src = src.toAbsolutePath(); // necessary because *see note above*
                        Path dst = dstRoot.resolve(srcRoot.relativize(src));
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst);
                    }
                }
            }
        }
    }
}
