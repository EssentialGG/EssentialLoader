package gg.essential.loader.stage2.jij;

import gg.essential.loader.stage2.data.FabricModJson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.*;

/**
 * Wraps a synthetic mod jar file providing easy methods to list, add and remove inner jars.
 */
public class SyntheticModJar implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Pattern INNER_JAR_NAME_PATTERN = Pattern.compile("(?<id>.+)@(?<version>.+)\\.jar");
    private static final String INNER_JAR_NAME_FORMAT = "%s@%s.jar";

    private final Path outerPath;
    private final FileSystem fileSystem;
    private final String modId;
    private final String modName;

    public SyntheticModJar(Path path, String modId, String modName) throws IOException {
        this.outerPath = path;
        this.fileSystem = FileSystems.newFileSystem(path, (ClassLoader) null);
        this.modId = modId;
        this.modName = modName;
    }

    private Path jarsFolder() throws IOException {
        return Files.createDirectories(fileSystem.getPath("META-INF", "jars"));
    }

    public List<InnerJar> getInnerJars() throws IOException {
        try (Stream<Path> stream = Files.list(jarsFolder())) {
            return stream.map(path -> {
                // Parse mod id and version from the inner jar name (we don't parse the inner jar's fabric.mod.json
                // because Java 8 doesn't support nested ZipFileSystems; and it's convenient to have it in the name)
                Matcher matcher = INNER_JAR_NAME_PATTERN.matcher(path.getFileName().toString());
                if (matcher.matches()) {
                    String id = matcher.group("id");
                    String versionStr = matcher.group("version");
                    return new InnerJar(id, versionStr);
                } else {
                    // This shouldn't happen unless someone manually messes with the file
                    LOGGER.error("Invalid inner jar name \"{}\" in \"{}\"", path, outerPath);
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        }
    }

    public void addInnerJar(Path source) throws IOException {
        FabricModJson modJson = FabricModJson.readFromJar(source);
        String fileName = String.format(INNER_JAR_NAME_FORMAT, modJson.getId(), modJson.getVersion());
        Path innerPath = jarsFolder().resolve(fileName);
        Files.copy(source, innerPath, REPLACE_EXISTING);
    }

    public void removeInnerJar(InnerJar innerJar) throws IOException {
        String fileName = String.format(INNER_JAR_NAME_FORMAT, innerJar.getId(), innerJar.getVersion());
        Path innerPath = jarsFolder().resolve(fileName);
        Files.delete(innerPath);
    }

    private void writeModJson() throws IOException {
        List<FabricModJson.Jar> jars;
        try (Stream<Path> stream = Files.list(jarsFolder())) {
            jars = stream
                .map(it -> it.toAbsolutePath().toString().substring(1))
                .map(FabricModJson.Jar::new)
                .collect(Collectors.toList());
        }
        Map<String, FabricModJson.VersionRange> depends = getInnerJars()
            .stream()
            .collect(Collectors.toMap(InnerJar::getId, it -> new FabricModJson.VersionRange(">=" + it.getVersion())));
        FabricModJson json = new FabricModJson(1, modId, "0", modName, depends, jars);
        FabricModJson.write(fileSystem.getPath("fabric.mod.json"), json);
    }

    @Override
    public void close() throws IOException {
        try {
            writeModJson();
        } finally {
            fileSystem.close();
        }
    }

    public static class InnerJar {
        private final String id;
        private final String version;

        public InnerJar(String id, String version) {
            this.id = id;
            this.version = version;
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return id + "@" + version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InnerJar that = (InnerJar) o;
            return Objects.equals(id, that.id) && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, version);
        }
    }
}
