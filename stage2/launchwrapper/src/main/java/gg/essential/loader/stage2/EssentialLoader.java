package gg.essential.loader.stage2;

import gg.essential.loader.stage2.relaunch.Relaunch;
import gg.essential.loader.stage2.util.Delete;
import gg.essential.loader.stage2.utils.Versions;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class EssentialLoader extends EssentialLoaderBase {
    public static final Logger LOGGER = LogManager.getLogger(EssentialLoader.class);
    private static final String MIXIN_TWEAKER = "org.spongepowered.asm.launch.MixinTweaker";
    private static final String STAGE1_TWEAKER = "gg.essential.loader.stage1.EssentialSetupTweaker";
    private static final String STAGE0_TWEAKERS_KEY = "essential.loader.stage2.stage0tweakers";
    private static final Set<String> STAGE0_TWEAKERS = new HashSet<>();

    private Path ourEssentialPath;
    private URL ourEssentialUrl;
    private URL ourMixinUrl;

    public EssentialLoader(Path gameDir, String gameVersion) {
        super(gameDir, gameVersion);
    }

    @Override
    protected Path postProcessDownload(Path downloadedFile) {

        // We need to strip the stage1 loader bundled in mods (to allow them to be dropped directly in the mods
        // folder) because it might be more recent than the version currently on the classpath and as such may prompt
        // an update of stage1 inside a relaunch (failing hard on Windows because the stage1 jar is currently loaded).
        // FIXME do we really have to do this next part? would be much nicer if we could treat Essential just like any
        //       other third-party mod here; and we can't just strip the Tweaker for third-party mods because those may
        //       actually need it.
        // We also need to strip the corresponding manifest entry because otherwise stage1 might try to load us as a
        // regular Essential-using mod, which won't actually work (Essential will function but it won't appear as a
        // mod in the Mods menu, etc.).
        try (FileSystem fileSystem = FileSystems.newFileSystem(downloadedFile, (ClassLoader) null)) {
            Path stage0Path = fileSystem.getPath("gg", "essential", "loader", "stage0");
            if (Files.exists(stage0Path)) {
                Delete.recursively(stage0Path);
            }

            Path manifestPath = fileSystem.getPath("META-INF", "MANIFEST.MF");
            if (Files.exists(manifestPath)) {
                Manifest manifest = new Manifest();
                try (InputStream in = Files.newInputStream(manifestPath)) {
                    manifest.read(in);
                }
                manifest.getMainAttributes().remove(new Attributes.Name("TweakClass"));
                // Specify OpenOptions here to bypass a bug in older openjdk versions (like the one the vanilla launcher uses
                // by default... *grumbles*).
                // See: https://github.com/openjdk/jdk8u/commit/bc2f17678c9607becb67f453c8b692c96d0e8bba#diff-2635ee58b104a22280e52e4140e2086f1a145bd9766c02a329a4ed25b01a972e
                try (OutputStream out = Files.newOutputStream(manifestPath, StandardOpenOption.TRUNCATE_EXISTING)) {
                    manifest.write(out);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to remove embedded stage0 from downloaded Essential jar:", e);
        }

        return super.postProcessDownload(downloadedFile);
    }

    @Override
    protected void loadPlatform() {
        if (ourEssentialPath == null || ourEssentialUrl == null || ourMixinUrl == null) {
            URL url = Launch.classLoader.findResource(CLASS_NAME.replace('.', '/') + ".class");
            if (url == null) {
                throw new RuntimeException("Failed to find Essential jar on classpath.");
            }
            if (!"jar".equals(url.getProtocol())) {
                throw new RuntimeException("Failed to find Essential jar on classpath, found URL with unexpected protocol: " + url);
            }
            try {
                ourEssentialUrl = new URL(url.getFile().substring(0, url.getFile().lastIndexOf('!')));
            } catch (MalformedURLException e) {
                throw new RuntimeException("Failed to find Essential jar on classpath, found URL with unexpected file: " + url, e);
            }
            try {
                ourEssentialPath = Paths.get(ourEssentialUrl.toURI());
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert Essential jar URL to Path: " + url, e);
            }
            ourMixinUrl = ourEssentialUrl;
        }

        preloadEssential(ourEssentialPath, ourEssentialUrl);

        try {
            injectMixinTweaker();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected ClassLoader getModClassLoader() {
        // FIXME we should ideally be using the launch class loader to load our bootstrap class but currently that
        //  causes our bootstrap code to break because the launch class loader creates a separate code source for
        //  each class rather than for the whole jar.
        //  We should switch this (because it allows us to use preloadLibrary on our api package) once our bootstrap
        //  loads fine under the launch class loader (the required change has been committed to master but needs to be
        //  deployed before we can switch here).
        // return Launch.classLoader;
        return Launch.classLoader.getClass().getClassLoader();
    }

    @Override
    protected void addToClasspath(Path path) {
        URL url;
        try {
            // Add to launch class loader
            url = path.toUri().toURL();
            Launch.classLoader.addURL(url);

            // FIXME only if jar has a tweaker. and if so, we need to chain-load that tweaker; maybe also the AT?
            // And its parent (for those classes that are excluded from the launch class loader)
            final ClassLoader classLoader = Launch.classLoader.getClass().getClassLoader();
            final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }

        // FIXME not everything that goes through here is necessarily Essential anymore
        ourEssentialPath = path;
        ourEssentialUrl = url;
        ourMixinUrl = url;
    }

    private void preloadEssential(Path path, URL url) {
        if (System.getProperty(Relaunch.FORCE_PROPERTY, "").equals("early")) {
            if (Relaunch.checkEnabled()) {
                Relaunch.relaunch(ourMixinUrl);
            }
        }

        String outdatedAsm = isAsmOutdated(url);
        if (outdatedAsm != null) {
            LOGGER.warn("Found an old version of ASM ({}). This may cause issues.", outdatedAsm);
            if (Relaunch.checkEnabled()) {
                Relaunch.relaunch(url);
            }
        }

        // Pre-load the resource cache of the launch class loader with the class files of some of our libraries.
        // Doing so will allow us to load our version, even if there is an older version already on the classpath
        // before our jar. This will of course only work if they have not already been loaded but in that case
        // there's really not much we can do about it anyway.
        try {
            Field classLoaderExceptionsField = LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
            classLoaderExceptionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> classLoaderExceptions = (Set<String>) classLoaderExceptionsField.get(Launch.classLoader);

            Field transformerExceptionsField = LaunchClassLoader.class.getDeclaredField("transformerExceptions");
            transformerExceptionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> transformerExceptions = (Set<String>) transformerExceptionsField.get(Launch.classLoader);

            // Some mods (BetterFoliage) will exclude kotlin from transformations, thereby voiding our pre-loading.
            boolean kotlinExcluded = Stream.concat(classLoaderExceptions.stream(), transformerExceptions.stream())
                .anyMatch(prefix -> prefix.startsWith("kotlin"));
            if (kotlinExcluded && !Relaunch.HAPPENED) {
                LOGGER.warn("Found Kotlin to be excluded from LaunchClassLoader transformations. This may cause issues.");
                LOGGER.debug("classLoaderExceptions:");
                for (String classLoaderException : classLoaderExceptions) {
                    LOGGER.debug("  - {}", classLoaderException);
                }
                LOGGER.debug("transformerExceptions:");
                for (String transformerException : transformerExceptions) {
                    LOGGER.debug("  - {}", transformerException);
                }
                if (Relaunch.checkEnabled()) {
                    throw new RelaunchRequest();
                }
            }

            // Some mods include signatures for all the classes in their jar, including Mixin. As a result, if any other
            // mod ships a Mixin version different from theirs (we likely do), it'll explode because of mis-matching
            // signatures.
            String signedMixinMod = findSignedMixin();
            if (signedMixinMod != null && !Relaunch.HAPPENED) {
                // To work around that, we'll re-launch. That works because our relaunch class loader does not implement
                // signature loading.
                LOGGER.warn("Found {}. This mod includes signatures for its bundled Mixin and will explode if " +
                    "a different Mixin version (even a more recent one) is loaded.", signedMixinMod);
                if (Relaunch.ENABLED) {
                    LOGGER.warn("Trying to work around the issue by re-launching which will ignore signatures.");
                } else {
                    LOGGER.warn("Cannot apply workaround because re-launching is disabled.");
                }
                if (Relaunch.checkEnabled()) {
                    throw new RelaunchRequest();
                }
            }

            Field resourceCacheField = LaunchClassLoader.class.getDeclaredField("resourceCache");
            resourceCacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, byte[]> resourceCache = (Map<String, byte[]>) resourceCacheField.get(Launch.classLoader);

            Field negativeResourceCacheField = LaunchClassLoader.class.getDeclaredField("negativeResourceCache");
            negativeResourceCacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> negativeResourceCache = (Set<String>) negativeResourceCacheField.get(Launch.classLoader);

            try (FileSystem fileSystem = FileSystems.newFileSystem(path, null)) {
                Path[] libs = {
                    fileSystem.getPath("kotlin"),
                    fileSystem.getPath("kotlinx", "coroutines"),
                    fileSystem.getPath("gg", "essential", "universal"),
                    fileSystem.getPath("gg", "essential", "elementa"),
                    fileSystem.getPath("gg", "essential", "vigilance"),
                    fileSystem.getPath("codes", "som", "anthony", "koffee"),
                    fileSystem.getPath("org", "kodein"),
                };
                for (Path libPath : libs) {
                    preloadLibrary(path, libPath, resourceCache, negativeResourceCache);
                }

                // Mixin is primarily a tweaker lib, so the chances of it having already been loaded by this point
                // are not nearly as small as non-tweaker libs. So, to reduce the chance of instability caused by
                // incompatible implementation classes, we only force our version if it is not already initialized.
                if (Launch.blackboard.get("mixin.initialised") == null) {
                    preloadLibrary(path, fileSystem.getPath("org", "spongepowered"), resourceCache, negativeResourceCache);
                }
            }

            if (Launch.classLoader.getClassBytes("pl.asie.foamfix.coremod.FoamFixCore") != null) {
                // FoamFix will by default replace the resource cache map with a weak one, thereby negating our hack.
                // To work around that, we preempt its replacement and put in a map which will throw an exception when
                // iterated.
                LOGGER.info("Detected FoamFix, locking LaunchClassLoader.resourceCache");
                resourceCacheField.set(Launch.classLoader, new ConcurrentHashMap<String,byte[]>(resourceCache) {
                    // FoamFix will call this before overwriting the resourceCache field
                    @Override
                    public Set<Entry<String, byte[]>> entrySet() {
                        throw new RuntimeException("Suppressing FoamFix LaunchWrapper weak resource cache.") {
                            // It'll then catch the exception and print it, which we can make less noisy.
                            @Override
                            public void printStackTrace() {
                                LOGGER.info(this.getMessage());
                            }
                        };
                    }
                });
            }
        } catch (RelaunchRequest relaunchRequest) {
            Relaunch.relaunch(url);
        } catch (Exception e) {
            LOGGER.error("Failed to pre-load dependencies: ", e);
        }
    }

    private void preloadLibrary(Path jarPath, Path libPath, Map<String, byte[]> resourceCache, Set<String> negativeResourceCache) throws IOException {
        if (Files.notExists(libPath)) {
            LOGGER.debug("Not pre-loading {} because it does not exist.", libPath);
            return;
        }

        LOGGER.debug("Pre-loading {} from {}..", libPath, jarPath);
        long start = System.nanoTime();

        Files.walkFileTree(libPath, new SimpleFileVisitor<Path>() {
            private static final String SUFFIX = ".class";
            private boolean warned;

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (path.getFileName().toString().endsWith(SUFFIX)) {
                    String file = path.toString().substring(1);
                    String name = file.substring(0, file.length() - SUFFIX.length()).replace('/', '.');
                    byte[] bytes = Files.readAllBytes(path);
                    byte[] oldBytes = resourceCache.put(name, bytes);
                    if (oldBytes != null && !Arrays.equals(oldBytes, bytes) && !warned) {
                        warned = true;
                        LOGGER.warn("Found potentially conflicting version of {} already loaded. This may cause issues.", libPath);
                        LOGGER.warn("First conflicting class: {}", name);
                        try {
                            LOGGER.warn("Likely source: {}", Launch.classLoader.findResource(file));
                        } catch (Throwable t) {
                            LOGGER.warn("Unable to determine likely source:", t);
                        }
                        if (Relaunch.checkEnabled()) {
                            throw new RelaunchRequest();
                        }
                    }
                    negativeResourceCache.remove(name);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        LOGGER.debug("Done after {}ns.", System.nanoTime() - start);
    }

    // Production requires usage of the MixinTweaker. Simply calling MixinBootstrap.init() will not always work, even
    // if it appears to work most of the time.
    // This code is a intentional duplicate of the one in stage1. The one over there is in case the third-party mod
    // relies on Mixin and runs even when stage2 cannot be loaded, this one is for Essential and we do not want to mix
    // the two (e.g. we might change how this one works in the future but we cannot easily change the one in stage1).
    private static void injectMixinTweaker() throws ClassNotFoundException, IllegalAccessException, InstantiationException, IOException {
        @SuppressWarnings("unchecked")
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");

        // If the MixinTweaker is already queued (because of another mod), then there's nothing we need to to
        if (tweakClasses.contains(MIXIN_TWEAKER)) {
            return;
        }

        // If it is already booted, we're also good to go
        if (Launch.blackboard.get("mixin.initialised") != null) {
            return;
        }

        System.out.println("Injecting MixinTweaker from EssentialLoader");

        // Otherwise, we need to take things into our own hands because the normal way to chainload a tweaker
        // (by adding it to the TweakClasses list during injectIntoClassLoader) is too late for Mixin.
        // Instead we instantiate the MixinTweaker on our own and add it to the current Tweaks list immediately.
        Launch.classLoader.addClassLoaderExclusion(MIXIN_TWEAKER.substring(0, MIXIN_TWEAKER.lastIndexOf('.')));
        @SuppressWarnings("unchecked")
        List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        tweaks.add((ITweaker) Class.forName(MIXIN_TWEAKER, true, Launch.classLoader).newInstance());
    }

    @Override
    protected void doInitialize() {
        detectStage0Tweaker();

        String outdatedMixin = isMixinOutdated();
        if (outdatedMixin != null) {
            LOGGER.warn("Found an old version of Mixin ({}). This may cause issues.", outdatedMixin);
            if (Relaunch.checkEnabled()) {
                Relaunch.relaunch(ourMixinUrl);
            }
        }

        if (System.getProperty(Relaunch.FORCE_PROPERTY, "").equals("late")) {
            if (Relaunch.checkEnabled()) {
                Relaunch.relaunch(ourMixinUrl);
            }
        }

        super.doInitialize();
    }

    private void detectStage0Tweaker() {
        Launch.blackboard.computeIfAbsent(STAGE0_TWEAKERS_KEY, k -> Collections.unmodifiableSet(STAGE0_TWEAKERS));

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stackTrace.length - 1; i++) {
            StackTraceElement element = stackTrace[i];
            if (element.getClassName().equals(STAGE1_TWEAKER) && element.getMethodName().equals("injectIntoClassLoader")) {
                STAGE0_TWEAKERS.add(stackTrace[i + 1].getClassName());
                break;
            }
        }
    }

    private String isMixinOutdated() {
        String loadedVersion = String.valueOf(Launch.blackboard.get("mixin.initialised"));
        String bundledVersion = Versions.getMixinVersion(ourMixinUrl);
        LOGGER.debug("Found Mixin {} loaded, we bundle {}", loadedVersion, bundledVersion);
        if (Versions.compare("mixin", loadedVersion, bundledVersion) < 0) {
            return loadedVersion;
        } else {
            return null;
        }
    }

    private String isAsmOutdated(URL ourUrl) {
        String loadedVersion = org.objectweb.asm.ClassWriter.class.getPackage().getImplementationVersion();
        String bundledVersion = Versions.getAsmVersion(ourUrl);
        LOGGER.debug("Found ASM {} loaded, we bundle {}", loadedVersion, bundledVersion);
        if (Versions.compare("ASM", loadedVersion, bundledVersion) < 0) {
            return loadedVersion;
        } else {
            return null;
        }
    }

    private String findSignedMixin() throws IOException {
        if (hasClass("net.darkhax.surge.Surge")) return "Surge";
        if (hasClass("me.jellysquid.mods.phosphor.core.PhosphorFMLLoadingPlugin")) return "Phosphor";
        return null;
    }

    private static boolean hasClass(String name) throws IOException {
        return Launch.classLoader.getClassBytes(name) != null;
    }

    private static class RelaunchRequest extends RuntimeException {}
}
