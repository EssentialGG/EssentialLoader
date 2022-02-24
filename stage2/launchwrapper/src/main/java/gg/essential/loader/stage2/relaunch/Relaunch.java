package gg.essential.loader.stage2.relaunch;

import gg.essential.loader.stage2.relaunch.args.LaunchArgs;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Relaunch {
    private static final Logger LOGGER = LogManager.getLogger(Relaunch.class);

    static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLTweaker";

    private static final String HAPPENED_PROPERTY = "essential.loader.relaunched";
    private static final String ENABLED_PROPERTY = "essential.loader.relaunch";

    /** Whether we are currently inside of a re-launch due to classpath complications. */
    public static final boolean HAPPENED = Boolean.parseBoolean(System.getProperty(HAPPENED_PROPERTY, "false"));
    /** Whether we should try to re-launch in case of classpath complications. */
    public static final boolean ENABLED = !HAPPENED && Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));

    public static boolean checkEnabled() {
        if (HAPPENED) {
            return false;
        }
        if (ENABLED) {
            return true;
        }
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("==================================================================================");
        LOGGER.warn("Essential can automatically attempt to fix this but this feature has been disabled");
        LOGGER.warn("because \"" + ENABLED_PROPERTY + "\" is set to false.");
        LOGGER.warn("");
        LOGGER.warn("THIS WILL CAUSE ISSUES, PROCEED AT YOUR OWN RISK!");
        LOGGER.warn("");
        LOGGER.warn("Remove \"-D" + ENABLED_PROPERTY + "=false\" from JVM args to enable re-launching.");
        LOGGER.warn("==================================================================================");
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("");
        return false;
    }

    public static void relaunch(URL essentialUrl) {
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("==================================================================================");
        LOGGER.warn("Attempting re-launch to load the newer version instead.");
        LOGGER.warn("");
        LOGGER.warn("If AND ONLY IF you know what you are doing, have fixed the issue manually and need");
        LOGGER.warn("to suppress this behavior (did you really fix it then?), you can set the");
        LOGGER.warn("\"" + ENABLED_PROPERTY + "\" system property to false.");
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

            // And because LaunchClassLoader.getSources is buggy and returns a List rather than a Set, we need to try
            // to remove the tweaker jars from the classpath, so we do not end up with duplicate entries in that List.
            // We cannot just remove everything after the first mod jar, cause there are mods like "performant" which
            // reflect into the URLClassPath and move themselves to the beginning..
            // So instead, we remove anything which declares a TweakClass which has in been loaded by the
            // CoreModManager.
            Set<String> tweakClasses = getTweakClasses();
            Iterator<URL> iterator = urls.iterator();
            iterator.next(); // skip Essential
            while (iterator.hasNext()) {
                URL url = iterator.next();
                if (isTweaker(url, tweakClasses)) {
                    iterator.remove();
                }
            }

            LOGGER.debug("Re-launching with classpath:");
            for (URL url : urls) {
                LOGGER.debug("    {}", url);
            }

            RelaunchClassLoader relaunchClassLoader = new RelaunchClassLoader(urls.toArray(new URL[0]), systemClassLoader);

            List<String> args = new ArrayList<>(LaunchArgs.guessLaunchArgs());
            String main = args.remove(0);

            Class<?> innerLaunch = Class.forName(main, false, relaunchClassLoader);
            Method innerMainMethod = innerLaunch.getDeclaredMethod("main", String[].class);
            innerMainMethod.invoke(null, (Object) args.toArray(new String[0]));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Unexpected re-launch failure", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
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

    @SuppressWarnings("unchecked")
    private static Set<String> getTweakClasses() {
        try {
            // We derive these from the tweakSorting field as it is common practice for Tweakers to remove themselves
            // from the more direct ignoredModFiles, whereas there is no reason to remove oneself the tweakSorting.
            Field field = Class.forName("net.minecraftforge.fml.relauncher.CoreModManager")
                .getDeclaredField("tweakSorting");
            field.setAccessible(true);
            Map<String, Integer> tweakSorting = (Map<String, Integer>) field.get(null);
            return tweakSorting.keySet();
        } catch (Exception e) {
            LOGGER.error("Failed to determine dynamically loaded tweaker classes.");
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    private static boolean isTweaker(URL url, Set<String> tweakClasses) {
        try {
            URI uri = url.toURI();
            if (!"file".equals(uri.getScheme())) {
                return false;
            }
            File file = new File(uri);
            if (!file.exists() || !file.isFile()) {
                return false;
            }
            try (JarFile jar = new JarFile(file)) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    return false;
                }
                return tweakClasses.contains(manifest.getMainAttributes().getValue("TweakClass"));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read manifest from " + url + ":", e);
            return false;
        }
    }
}
