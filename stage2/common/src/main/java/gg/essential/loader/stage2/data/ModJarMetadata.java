package gg.essential.loader.stage2.data;

import gg.essential.loader.stage2.util.Checksum;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static java.nio.file.StandardOpenOption.CREATE;

public class ModJarMetadata {
    public static final ModJarMetadata EMPTY = new ModJarMetadata(ModId.UNKNOWN, ModVersion.UNKNOWN, null, null);

    private final @NotNull ModId mod;
    private final @NotNull ModVersion version;
    private final String platform;
    private final String checksum;

    public ModJarMetadata(@NotNull ModId mod, @NotNull ModVersion version, String platform, String checksum) {
        this.mod = mod;
        this.version = version;
        this.platform = platform;
        this.checksum = checksum;
    }

    public @NotNull ModId getMod() {
        return mod;
    }

    public @NotNull ModVersion getVersion() {
        return version;
    }

    public String getPlatform() {
        return platform;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModJarMetadata that = (ModJarMetadata) o;
        return mod.equals(that.mod) && version.equals(that.version) && Objects.equals(platform, that.platform) && Objects.equals(checksum, that.checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mod, version, platform, checksum);
    }

    public static Path metaFilePath(Path jarFile) {
        return jarFile.resolveSibling(jarFile.getFileName() + ".meta");
    }

    public void writeToMetaFile(Path jarFile) throws IOException {
        try (OutputStream out = Files.newOutputStream(metaFilePath(jarFile), CREATE)) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1"); // unused but required by JRE
            write(manifest.getMainAttributes());
            manifest.write(out);
        }
    }

    public void writeToJarFile(Path jarFile) throws IOException {
        try (FileSystem fileSystem = FileSystems.newFileSystem(jarFile, (ClassLoader) null)) {
            Path manifestPath = fileSystem.getPath("META-INF", "MANIFEST.MF");

            Manifest manifest = new Manifest();
            if (Files.exists(manifestPath)) {
                try (InputStream in = Files.newInputStream(manifestPath)) {
                    manifest.read(in);
                }
            }

            write(manifest.getMainAttributes());

            try (OutputStream out = Files.newOutputStream(manifestPath, CREATE)) {
                manifest.write(out);
            }
        }
    }

    public void write(Attributes attributes) {
        put(attributes, "Essential-Mod-Publisher-Id", mod.getPublisherId());
        put(attributes, "Essential-Mod-Publisher-Slug", mod.getPublisherSlug());
        put(attributes, "Essential-Mod-Id", mod.getModId());
        put(attributes, "Essential-Mod-Slug", mod.getModSlug());
        put(attributes, "Essential-Mod-Version-Id", version.getId());
        put(attributes, "Essential-Mod-Version", version.getVersion());
        put(attributes, "Essential-Mod-Platform", platform);
        put(attributes, "Essential-Mod-Checksum", checksum);
    }

    private static void put(Attributes attributes, String key, String value) {
        if (value != null) {
            attributes.putValue(key, value);
        }
    }

    public static ModJarMetadata read(Attributes attributes) {
        return new ModJarMetadata(
            new ModId(
                attributes.getValue("Essential-Mod-Publisher-Id"),
                attributes.getValue("Essential-Mod-Publisher-Slug"),
                attributes.getValue("Essential-Mod-Id"),
                attributes.getValue("Essential-Mod-Slug")
            ),
            new ModVersion(
                attributes.getValue("Essential-Mod-Version-Id"),
                attributes.getValue("Essential-Mod-Version")
            ),
            attributes.getValue("Essential-Mod-Platform"),
            attributes.getValue("Essential-Mod-Checksum")
        );
    }

    public static ModJarMetadata readFromMetaFile(Path jarFile) throws IOException {
        ModJarMetadata metadata = ModJarMetadata.EMPTY;
        Path manifestPath = metaFilePath(jarFile);
        if (Files.exists(manifestPath)) {
            try (InputStream in = Files.newInputStream(manifestPath)) {
                metadata = read(new Manifest(in).getMainAttributes());
            }
        }
        return metadata;
    }

    public static ModJarMetadata readFromJarFile(Path jarFile) throws IOException {
        ModJarMetadata metadata = ModJarMetadata.EMPTY;
        try (FileSystem fileSystem = FileSystems.newFileSystem(jarFile, (ClassLoader) null)) {
            Path manifestPath = fileSystem.getPath("META-INF", "MANIFEST.MF");
            if (Files.exists(manifestPath)) {
                try (InputStream in = Files.newInputStream(manifestPath)) {
                    metadata = read(new Manifest(in).getMainAttributes());
                }
            }
        }
        if (metadata.getChecksum() == null) {
            String checksum = Checksum.getChecksum(jarFile);
            metadata = new ModJarMetadata(metadata.mod, metadata.version, metadata.platform, checksum);
        }
        return metadata;
    }
}
