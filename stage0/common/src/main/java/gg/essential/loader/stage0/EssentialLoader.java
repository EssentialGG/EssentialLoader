package gg.essential.loader.stage0;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

class EssentialLoader {
    static final String STAGE1_RESOURCE = "gg/essential/loader/stage0/stage1.jar";
    static final String STAGE1_PKG = "gg.essential.loader.stage1.";
    static final String STAGE1_PKG_PATH = STAGE1_PKG.replace('.', '/');
    static final Logger LOGGER = LogManager.getLogger(EssentialLoader.class);

    private final String variant;

    public EssentialLoader(String variant) {
        this.variant = variant;
    }

    public Path loadStage1File(Path gameDir) throws Exception {
        final Path dataDir = gameDir
            .resolve("essential")
            .resolve("loader")
            .resolve("stage0")
            .resolve(variant);
        final Path stage1UpdateFile = dataDir.resolve("stage1.update.jar");
        final Path stage1File = dataDir.resolve("stage1.jar");
        final URL stage1Url = stage1File.toUri().toURL();

        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        // If there's an update, use that
        // (we cannot just replace the file at runtime because of file locks the JVM holds on it)
        if (Files.exists(stage1UpdateFile)) {
            LOGGER.info("Found update for stage1.");
            Files.deleteIfExists(stage1File);
            Files.move(stage1UpdateFile, stage1File);
        }

        // Check to see if there is a newer stage1 version somewhere on the classpath
        URL latestUrl = null;
        int latestVersion = -1;

        // newer than the already extracted one, that is
        if (Files.exists(stage1File)) {
            latestVersion = getVersion(stage1Url);
            LOGGER.debug("Found stage1 version {}: {}", latestVersion, stage1Url);
        }

        Enumeration<URL> resources = EssentialLoader.class.getClassLoader().getResources(STAGE1_RESOURCE);
        if (!resources.hasMoreElements()) {
            LOGGER.warn("Found no embedded stage1 jar files.");
        }
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            int version = getVersion(url);
            LOGGER.debug("Found stage1 version {}: {}", version, url);
            if (version > latestVersion) {
                latestVersion = version;
                latestUrl = url;
            }
        }

        // If there is a jar which is newer than the extracted one, use it instead
        if (latestUrl != null) {
            LOGGER.info("Updating stage1 to version {} from {}", latestVersion, latestUrl);
            try (InputStream in = latestUrl.openStream()) {
                Files.deleteIfExists(stage1File);
                Files.copy(in, stage1File);
            }
        }

        // Ready for class loading
        return stage1File;
    }

    private static int getVersion(URL file) {
        try (JarInputStream in = new JarInputStream(file.openStream(), false)) {
            Manifest manifest = in.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            if (!STAGE1_PKG_PATH.equals(attributes.getValue("Name"))) {
                return -1;
            }
            return Integer.parseInt(attributes.getValue("Implementation-Version"));
        } catch (Exception e) {
            LOGGER.warn("Failed to read version from " + file, e);
            return -1;
        }
    }
}
