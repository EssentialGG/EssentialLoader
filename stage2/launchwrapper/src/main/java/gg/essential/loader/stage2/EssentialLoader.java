package gg.essential.loader.stage2;

import gg.essential.loader.stage2.data.ModJarMetadata;
import gg.essential.loader.stage2.relaunch.Relaunch;
import gg.essential.loader.stage2.util.Delete;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static gg.essential.loader.stage2.Utils.findMostRecentFile;
import static gg.essential.loader.stage2.Utils.findNextMostRecentFile;

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

    private void deleteEmbeddedStage0(Path downloadedFile) throws IOException {
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
        }
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

        if (Relaunch.checkEnabled()) {
            Relaunch.relaunch(ourMixinUrl);
        }

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
    protected void addToClasspath(Mod mod, ModJarMetadata jarMeta, Path mainJar, List<Path> innerJars) {
        if (mod.isEssential()) {
            // If we were to load the downloaded Essential jar directly, we will run into issues if the game goes on to
            // relaunch. See [deleteEmbeddedStage0] for details.
            // To prevent that, we'll create a copy of the downloaded jar, delete the embedded stage0 from that, and
            // then add that jar to the classpath instead.
            // We don't just modify the original directly because that would mess up its checksum.
            try {
                String fileBaseName = mod.fileBaseName + ".processed";
                Path processedMainJar = findMostRecentFile(mod.dataDir, fileBaseName, FILE_EXTENSION).getKey();

                ModJarMetadata processedMeta = ModJarMetadata.EMPTY;
                if (Files.exists(processedMainJar)) {
                    try {
                        processedMeta = ModJarMetadata.readFromJarFile(processedMainJar);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to read existing processed jar metadata", e);
                    }
                }

                if (!processedMeta.equals(jarMeta)) {
                    Path tmpFile = Files.createTempFile(processedMainJar.getParent(), "processing", ".jar");
                    Files.copy(mainJar, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                    deleteEmbeddedStage0(tmpFile);
                    jarMeta.writeToJarFile(tmpFile);

                    try {
                        Files.deleteIfExists(processedMainJar);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to delete old processed file, will try again later.", e);
                    }

                    // If we succeeded in deleting that file, we might now be able to write to a lower-numbered one
                    // and if not, we need to write to the next higher one.
                    processedMainJar = findNextMostRecentFile(mod.dataDir, fileBaseName, FILE_EXTENSION);

                    Files.move(tmpFile, processedMainJar);
                }

                mainJar = processedMainJar;
            } catch (IOException e) {
                LOGGER.warn("Failed to post-process downloaded Essential jar:", e);
            }
        }
        super.addToClasspath(mod, jarMeta, mainJar, innerJars);
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
}
