package gg.essential.loader.stage2.relaunch;

import gg.essential.loader.stage2.relaunch.args.LaunchArgs;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.spi.LoggerContext;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@SuppressWarnings("UrlHashCode") // all our urls are local files
public class Relaunch {
    private static final Logger LOGGER = LogManager.getLogger(Relaunch.class);

    static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLTweaker";

    private static final String HAPPENED_PROPERTY = "essential.loader.relaunched";

    public static void relaunch(Set<URL> prioritizedUrls) {
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("==================================================================================");
        LOGGER.warn("Re-launching to load the newer versions of mods/libraries.");
        LOGGER.warn("==================================================================================");
        LOGGER.warn("");
        LOGGER.warn("");
        LOGGER.warn("");

        // Set marker for our tests
        System.setProperty(HAPPENED_PROPERTY, "true");

        // Clean up certain global state
        cleanupForRelaunch();

        try {
            // Get the system class loader (in prod, this will be the actual system class loader, in tests it will be
            // the IsolatedLaunch classloader), we can rely on it being a URLClassLoader cause Launch does the same.
            URLClassLoader systemClassLoader = (URLClassLoader) Launch.class.getClassLoader();
            // Get the classpath from the system class loader, this will have had various tweaker mods appended to it.
            List<URL> urls = new ArrayList<>(Arrays.asList(systemClassLoader.getURLs()));

            // And because LaunchClassLoader.getSources is buggy and returns a List rather than a Set, we need to try
            // to remove the tweaker jars from the classpath, so we do not end up with duplicate entries in that List.
            // We cannot just remove everything after the first mod jar, cause there are mods like "performant" which
            // reflect into the URLClassPath and move themselves to the beginning..
            // So instead, we remove anything which declares a TweakClass which has in been loaded by the
            // CoreModManager.
            Set<String> tweakClasses = getTweakClasses();
            urls.removeIf(url -> isTweaker(url, tweakClasses));

            // Finally make sure our urls are on the classpath and before any other mod
            urls.removeIf(prioritizedUrls::contains);
            urls.addAll(0, prioritizedUrls);

            LOGGER.debug("Re-launching with classpath:");
            for (URL url : urls) {
                LOGGER.debug("    {}", url);
            }

            RelaunchClassLoader relaunchClassLoader = new RelaunchClassLoader(urls.toArray(new URL[0]), systemClassLoader);
            // Make it available for introspection in our tests
            Launch.blackboard.put("gg.essential.loader.stage2.relaunchClassLoader", relaunchClassLoader);

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

        try {
            cleanupMixinAppender();
        } catch (Throwable t) {
            LOGGER.error("Failed to reset mixin appender. INIT-phase mixins may misfunction.", t);
        }
    }

    // Mixin detects the start of the INIT phase by listening to log messages via its Appender. With non-beta log4j2
    // if the inner mixin tries to do the same, its appender will be rejected, and it'll never be able to transition
    // into the INIT phase, skipping all mixins registered for that phase.
    // See MixinPlatformAgentFMLLegacy.MixinAppender
    // To fix that, we remove the outer mixin's appender before relaunching.
    private static void cleanupMixinAppender() throws ReflectiveOperationException {
        // Note: Need to get a logger context by class loader here, otherwise this won't work if the Relaunch class is
        //       loaded in a different ClassLoader (even if it's a child) than the above mixin class
        LoggerContext context = LogManager.getContext(Launch.class.getClassLoader(), false);
        // Using reflection to call this method because its exact return type differs between beta9 and release log4j2
        Logger fmlLogger = (Logger) LoggerContext.class.getMethod("getLogger", String.class).invoke(context, "FML");
        if (fmlLogger instanceof org.apache.logging.log4j.core.Logger) {
            org.apache.logging.log4j.core.Logger fmlLoggerImpl = (org.apache.logging.log4j.core.Logger) fmlLogger;
            Appender mixinAppender = fmlLoggerImpl.getAppenders().get("MixinLogWatcherAppender");
            if (mixinAppender != null) {
                fmlLoggerImpl.removeAppender(mixinAppender);
            }
        }
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
