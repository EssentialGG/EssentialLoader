package gg.essential.loader.stage0;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("unused")
public class EssentialSetupTweaker implements ITweaker {
    private static final String STAGE1_PKG = "gg.essential.loader.stage1.";
    private static final String STAGE1_CLS = STAGE1_PKG + "EssentialSetupTweaker";
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
            Files.deleteIfExists(stage1File);
            Files.move(stage1UpdateFile, stage1File);
        }

        // If there is no stage1 file yet, extract our built-in one
        if (!Files.exists(stage1File)) {
            try (InputStream in = EssentialSetupTweaker.class.getResourceAsStream("stage1.jar")) {
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
