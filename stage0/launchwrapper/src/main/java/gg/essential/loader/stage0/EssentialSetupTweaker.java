package gg.essential.loader.stage0;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

@SuppressWarnings("unused")
public class EssentialSetupTweaker implements ITweaker {
    private static final String STAGE1_RESOURCE = "gg/essential/loader/stage0/stage1.jar";
    private static final String STAGE1_PKG = "gg.essential.loader.stage1.";
    private static final String STAGE1_PKG_PATH = STAGE1_PKG.replace('.', '/');
    private static final String STAGE1_CLS = STAGE1_PKG + "EssentialSetupTweaker";
    private static final Logger LOGGER = LogManager.getLogger(EssentialSetupTweaker.class);
    private final ITweaker stage1;

    public EssentialSetupTweaker() {
        try {
            this.stage1 = loadStage1(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ITweaker loadStage1(ITweaker stage0) throws Exception {
        // ForgeGradle just doesn't pass a game dir even though it's a required argument...
        // So we need to set this manually to allow people to launch in the development environment without requiring
        // additional setup.
        if (Launch.minecraftHome == null) {
            Launch.minecraftHome = new File(".");
        }

        final Path dataDir = Launch.minecraftHome.toPath()
            .resolve("essential")
            .resolve("loader")
            .resolve("stage0")
            .resolve("launchwrapper");
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

        Enumeration<URL> resources = EssentialSetupTweaker.class.getClassLoader().getResources(STAGE1_RESOURCE);
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

        // Add stage1 file to launch class loader (with an exception) and its parent (which will end up load it)
        LaunchClassLoader classLoader = Launch.classLoader;
        classLoader.addURL(stage1Url);
        classLoader.addClassLoaderExclusion(STAGE1_PKG);
        addUrlHack(classLoader.getClass().getClassLoader(), stage1Url);

        // Finally, load stage1
        return (ITweaker) Class.forName(STAGE1_CLS, true, classLoader)
            .getConstructor(ITweaker.class)
            .newInstance(stage0);
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

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.stage1.acceptOptions(args, gameDir, assetsDir, profile);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        this.stage1.injectIntoClassLoader(classLoader);
    }

    @Override
    public String getLaunchTarget() {
        return this.stage1.getLaunchTarget();
    }

    @Override
    public String[] getLaunchArguments() {
        return this.stage1.getLaunchArguments();
    }

    private static void addUrlHack(ClassLoader loader, URL url) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // This breaks if the parent class loader is not a URLClassLoader, but so does Forge, so we should be fine.
        final ClassLoader classLoader = Launch.classLoader.getClass().getClassLoader();
        final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        method.setAccessible(true);
        method.invoke(classLoader, url);
    }
}
