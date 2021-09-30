package gg.essential.loader.stage2;

import com.google.common.primitives.Bytes;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class RuntimeModRemapper {
    private static final Logger LOGGER = LogManager.getLogger(RuntimeModRemapper.class);

    private final EssentialLoader.LoaderInternals loaderInternals;

    public RuntimeModRemapper(EssentialLoader.LoaderInternals loaderInternals) {
        this.loaderInternals = loaderInternals;
    }

    /**
     * Fetches data about any inputs used in remapping, producing a checksum which we can compare cached results
     * against.
     */
    private byte[] gatherRemapInputs(final Path modPath) throws IOException {
        URL mappings = FabricLauncherBase.class.getClassLoader().getResource("mappings/mappings.tiny");
        if (mappings == null) {
            throw new RuntimeException("Failed to find tiny mappings file.");
        }

        String remapClasspathFile = System.getProperty("fabric.remapClasspathFile");
        if (remapClasspathFile == null) {
            throw new RuntimeException("Remap classpath file property not set. Using an ancient Loom version?");
        }

        return DigestUtils.sha1(Bytes.concat(
            mappings.toString().getBytes(StandardCharsets.UTF_8),
            Files.readAllBytes(Paths.get(remapClasspathFile)),
            Files.readAllBytes(modPath)
        ));
    }

    public Path remap(final Path path, final ModMetadata metadata) throws Exception {
        final Path devPath = Utils.mapFileBaseName(path, name -> name + "-dev");
        final Path devInputs = devPath.resolveSibling(devPath.getFileName().toString() + ".inputs");
        final byte[] currentInputs = gatherRemapInputs(path);

        if (!Files.exists(devPath) || !Files.exists(devInputs) || !Arrays.equals(Files.readAllBytes(devInputs), currentInputs)) {
            Files.deleteIfExists(devPath);
            Files.deleteIfExists(devInputs);

            LOGGER.info("Remapping Essential to development mappings...");
            LOGGER.info("This may take a few seconds but will only happen once (or when mappings/classpath change).");

            URL remappedUrl = loaderInternals.remapMap(metadata, path.toUri().toURL());

            try (InputStream in = remappedUrl.openStream()) {
                Files.copy(in, devPath);
                Files.write(devInputs, currentInputs, StandardOpenOption.CREATE_NEW);
            }
        }

        return devPath;
    }
}
