package gg.essential.loader.stage2;

import gg.essential.loader.stage2.relaunch.Relaunch;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class EssentialLoader extends EssentialLoaderBase {
    private static final Logger LOGGER = LogManager.getLogger(EssentialLoader.class);
    private static final String MIXIN_TWEAKER = "org.spongepowered.asm.launch.MixinTweaker";

    private URL ourMixinUrl;

    public EssentialLoader(Path gameDir, String gameVersion) {
        super(gameDir, gameVersion, false);
    }

    @Override
    protected void loadPlatform() {
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
    protected void addToClasspath(final File file) {
        Path path = file.toPath();
        URL url;
        try {
            // Add to launch class loader
            url = file.toURI().toURL();
            Launch.classLoader.addURL(url);

            // And its parent (for those classes that are excluded from the launch class loader)
            final ClassLoader classLoader = Launch.classLoader.getClass().getClassLoader();
            final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }

        ourMixinUrl = url;

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
                if (Relaunch.ENABLED) {
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
                        if (Relaunch.ENABLED) {
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

    @Override
    protected boolean isInClassPath() {
        try {
            LinkedHashSet<String> objects = new LinkedHashSet<>();
            objects.add(CLASS_NAME);
            Launch.classLoader.clearNegativeEntries(objects);
            Class.forName(CLASS_NAME);
            return true;
        } catch (ClassNotFoundException ignored) { }
        return false;
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
        String outdatedMixin = isMixinOutdated();
        if (outdatedMixin != null) {
            LOGGER.warn("Found an old version of Mixin ({}). This may cause issues.", outdatedMixin);
            if (Relaunch.ENABLED) {
                Relaunch.relaunch(ourMixinUrl);
            }
        }

        super.doInitialize();
    }

    private String isMixinOutdated() {
        String loadedVersion = String.valueOf(Launch.blackboard.get("mixin.initialised"));
        String bundledVersion = getMixinVersion(ourMixinUrl);
        int[] loadedParts = parseMixinVersion(loadedVersion);
        int[] bundledParts = parseMixinVersion(bundledVersion);
        if (loadedParts == null || bundledParts == null) {
            return null;
        }
        for (int i = 0; i < loadedParts.length; i++) {
            if (loadedParts[i] < bundledParts[i]) {
                return loadedVersion;
            } else if (loadedParts[i] > bundledParts[i]) {
                break;
            }
        }
        return null;
    }

    private int[] parseMixinVersion(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("[.-]");
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length && i < numbers.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                LOGGER.warn("Failed to parse mixin version \"{}\".", version);
                LOGGER.debug(e);
                return null;
            }
        }
        return numbers;
    }

    private String getMixinVersion(URL ourMixinUrl) {
        try (FileSystem fileSystem = FileSystems.newFileSystem(asJar(ourMixinUrl.toURI()), Collections.emptyMap())) {
            Path bootstrapPath = fileSystem.getPath("org", "spongepowered", "asm", "launch", "MixinBootstrap.class");
            try (InputStream inputStream = Files.newInputStream(bootstrapPath)) {
                ClassReader reader = new ClassReader(inputStream);
                ClassNode classNode = new ClassNode(Opcodes.ASM5);
                reader.accept(classNode, 0);
                for (FieldNode field : classNode.fields) {
                    if (field.name.equals("VERSION")) {
                        return String.valueOf(field.value);
                    }
                }
                LOGGER.warn("Failed to determine version of bundled mixin: no VERSION field in MixinBootstrap");
            }
        } catch (URISyntaxException | IOException e) {
            LOGGER.warn("Failed to determine version of bundled mixin:", e);
        }
        return null;
    }

    private static class RelaunchRequest extends RuntimeException {}
}
