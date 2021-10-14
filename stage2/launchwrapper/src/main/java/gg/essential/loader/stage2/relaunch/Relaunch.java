package gg.essential.loader.stage2.relaunch;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Relaunch {
    private static final Logger LOGGER = LogManager.getLogger(Relaunch.class);

    static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLTweaker";

    private static final String HAPPENED_PROPERTY = "essential.loader.relaunched";
    private static final String ENABLED_PROPERTY = "essential.loader.relaunch";

    /** Whether we are currently inside of a re-launch due to classpath complications. */
    public static final boolean HAPPENED = Boolean.parseBoolean(System.getProperty(HAPPENED_PROPERTY, "false"));
    /** Whether we should try to re-launch in case of classpath complications. */
    public static final boolean ENABLED = !HAPPENED && Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));

    public static void relaunch(URL essentialUrl) {
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("==================================================================================");
        LOGGER.warn("Attempting re-launch to load the newer version instead.");
        LOGGER.warn("Add \"-D" + ENABLED_PROPERTY + "=false\" to JVM args to disable re-launching.");
        LOGGER.warn("==================================================================================");
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("");

        // Set marker so we do not end up in a loop
        System.setProperty(HAPPENED_PROPERTY, "true");

        // Clean up certain global state
        cleanupForRelaunch();

        try {
            // Get the system class loader (in prod, this will be the actual system class loader, in tests it will be
            // the IsolatedLaunch classloader), we can rely on it being a URLClassLoader cause Launch does the same.
            URLClassLoader systemClassLoader = (URLClassLoader) Launch.class.getClassLoader();
            // Get the classpath from the system class loader, this will have had various tweaker mods appended to it.
            List<URL> urls = new ArrayList<>(Arrays.asList(systemClassLoader.getURLs()));

            // So we need to make sure Essential is on the classpath before any other mod
            urls.remove(essentialUrl);
            urls.add(0, essentialUrl);

            LOGGER.debug("Re-launching with classpath:");
            for (URL url : urls) {
                LOGGER.debug("    {}", url);
            }

            RelaunchClassLoader relaunchClassLoader =
                new RelaunchClassLoader(urls.toArray(new URL[0]), systemClassLoader, essentialUrl);

            Class<?> innerLaunch = Class.forName(Launch.class.getName(), false, relaunchClassLoader);
            Method innerMainMethod = innerLaunch.getDeclaredMethod("main", String[].class);
            innerMainMethod.invoke(null, (Object) getLaunchArgs());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unexpected re-launch failure", e);
        } finally {
            // Clear marker. This only relevant for our tests, production calls System.exit and never returns.
            System.clearProperty(HAPPENED_PROPERTY);
        }
    }

    private static void cleanupForRelaunch() {
        // https://github.com/MinimallyCorrect/ModPatcher/blob/3a538a5b574546f68d927f3551bf9e61fda4a334/src/main/java/org/minimallycorrect/modpatcher/api/ModPatcherTransformer.java#L43-L51
        System.clearProperty("nallar.ModPatcher.alreadyLoaded");
        // https://github.com/MinimallyCorrect/ModPatcher/blob/3a538a5b574546f68d927f3551bf9e61fda4a334/src/main/java/org/minimallycorrect/modpatcher/api/LaunchClassLoaderUtil.java#L31-L36
        System.clearProperty("nallar.LaunchClassLoaderUtil.alreadyLoaded");
    }

    private static String[] getLaunchArgs() {
        // These are made available by FMLTweaker. This does unfortunately not include the keyword-less arguments but
        // I could find no way to access them and Vanilla doesn't use those anyway, so it should be fine.
        @SuppressWarnings("unchecked")
        Map<String, String> launchArgs = (Map<String, String>) Launch.blackboard.get("launchArgs");

        List<String> result = new ArrayList<>();

        // Tweaker arguments are consumed by Launch.launch, I see no way to get them so we'll just assume it's always
        // FML, that should be the case for production in any ordinary setup.
        result.add("--tweakClass");
        result.add(FML_TWEAKER);

        for (Map.Entry<String, String> entry : launchArgs.entrySet()) {
            result.add(entry.getKey());
            result.add(entry.getValue());
        }

        return result.toArray(new String[0]);
    }
}
