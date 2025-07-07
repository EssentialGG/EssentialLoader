package gg.essential.loader.stage2;

import gg.essential.loader.stage2.compat.BetterFpsTransformerWrapper;
import gg.essential.loader.stage2.compat.PhosphorTransformer;
import gg.essential.loader.stage2.compat.ThreadUnsafeTransformersListWorkaround;
import gg.essential.loader.stage2.compat.tweaker.BetterFpsWrappingTweaker;
import gg.essential.loader.stage2.util.MixinTweakerInjector;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class RelaunchedLoader {
    private static final Logger LOGGER = LogManager.getLogger(RelaunchedLoader.class);

    private final RelaunchInfo relaunchInfo;
    private final List<SourceFile> sourceFiles;
    private boolean injected;

    RelaunchedLoader(RelaunchInfo relaunchInfo) {
        this.relaunchInfo = relaunchInfo;

        sourceFiles = SourceFile.readInfos(Launch.classLoader.getSources());

        if (relaunchInfo.loadedIds.contains("mixin") || /* older versions of */ relaunchInfo.loadedIds.contains("essential")) {
            MixinTweakerInjector.injectMixinTweaker(true);
        }
    }

    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        if (injected) return;
        injected = true;

        if (relaunchInfo.loadedIds.contains("io.github.llamalad7:mixinextras-common")) {
            try {
                Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap", false, classLoader)
                    .getMethod("init")
                    .invoke(null);
            } catch (Throwable e) {
                LOGGER.error("Failed to initialize MixinExtras", e);
            }
        }

        if (relaunchInfo.loadedIds.contains("gg.essential.lib:mixinextras")) {
            try {
                Class.forName("gg.essential.lib.mixinextras.MixinExtrasBootstrap", false, classLoader)
                    .getMethod("init")
                    .invoke(null);
            } catch (Throwable e) {
                LOGGER.error("Failed to initialize MixinExtras", e);
            }
        }

        if (relaunchInfo.loadedIds.contains("essential")) {
            try {
                Class.forName("gg.essential.api.tweaker.EssentialTweaker", false, classLoader)
                    .getDeclaredMethod("initialize", File.class)
                    .invoke(null, Launch.minecraftHome != null ? Launch.minecraftHome : new File("."));
            } catch (Throwable e) {
                LOGGER.error("Failed to initialize Essential mod", e);
            }
        }


        //
        // Compatibility patches for various third-party mods
        //

        ThreadUnsafeTransformersListWorkaround.apply();

        if (relaunchInfo.loadedIds.contains("mixin")) {
            addMixinTransformerExclusion("bre.smoothfont.asm.Transformer"); // fails silently if called more than once
            addMixinTransformerExclusion("com.therandomlabs.randompatches.core.RPTransformer");
            addMixinTransformerExclusion("lakmoore.sel.common.Transformer");
            addMixinTransformerExclusion("openmods.core.OpenModsClassTransformer");
            addMixinTransformerExclusion("net.creeperhost.launchertray.transformer.MinecraftTransformer");
            addMixinTransformerExclusion("vazkii.quark.base.asm.ClassTransformer");
            addMixinTransformerExclusion(BetterFpsTransformerWrapper.class.getName());
        }

        BetterFpsWrappingTweaker.inject();

        Launch.classLoader.registerTransformer(PhosphorTransformer.class.getName());
    }

    public void initialize(ITweaker stage0Tweaker) {
        String tweakerName = stage0Tweaker.getClass().getName();
        for (SourceFile sourceFile : sourceFiles) {
            if (tweakerName.equals(sourceFile.tweaker) && !sourceFile.initialized) {
                sourceFile.initialized = true;
                try {
                    setupSourceFile(sourceFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setupSourceFile(SourceFile sourceFile) throws Exception {
        // Forge will by default ignore a mod file if it contains a tweaker
        // So we need to remove the mod from that exclusion list
        Field ignoredModFile = CoreModManager.class.getDeclaredField("ignoredModFiles");
        ignoredModFile.setAccessible(true);
        ((List<String>) ignoredModFile.get(null)).remove(sourceFile.file.getName());

        // And instead add ourselves to the mod candidate list
        CoreModManager.getReparseableCoremods().add(sourceFile.file.getName());

        // FML will not load CoreMods if it finds a tweaker, so we need to load the coremod manually if present
        // We do this to reduce the friction of adding our tweaker if a mod has previously been relying on a
        // coremod (cause ordinarily they would have to convert their coremod into a tweaker manually).
        // Mixin takes care of this as well, so we mustn't if it will.
        String coreMod = sourceFile.coreMod;
        if (coreMod != null && !sourceFile.mixin) {
            loadCoreMod(sourceFile.file, coreMod);
        }

        // If they declared our tweaker but also want to use mixin, then we'll inject the mixin tweaker
        // for them.
        if (sourceFile.mixin) {
            MixinTweakerInjector.injectMixinTweaker(false);

            // Mixin will only look at jar files which declare the MixinTweaker as their tweaker class, so we need
            // to manually add our source files for inspection.
            try {
                Class<?> MixinBootstrap = Class.forName("org.spongepowered.asm.launch.MixinBootstrap");
                Class<?> MixinPlatformManager = Class.forName("org.spongepowered.asm.launch.platform.MixinPlatformManager");
                Object platformManager = MixinBootstrap.getDeclaredMethod("getPlatform").invoke(null);
                Method addContainer;
                Object arg;
                try {
                    // Mixin 0.7
                    addContainer = MixinPlatformManager.getDeclaredMethod("addContainer", URI.class);
                    arg = sourceFile.file.toURI();
                } catch (NoSuchMethodException ignored) {
                    // Mixin 0.8
                    Class<?> IContainerHandle = Class.forName("org.spongepowered.asm.launch.platform.container.IContainerHandle");
                    Class<?> ContainerHandleURI = Class.forName("org.spongepowered.asm.launch.platform.container.ContainerHandleURI");
                    addContainer = MixinPlatformManager.getDeclaredMethod("addContainer", IContainerHandle);
                    arg = ContainerHandleURI.getDeclaredConstructor(URI.class).newInstance(sourceFile.file.toURI());
                }
                addContainer.invoke(platformManager, arg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCoreMod(File file, String coreMod) throws ReflectiveOperationException {
        Method loadCoreMod = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
        loadCoreMod.setAccessible(true);
        ITweaker tweaker = (ITweaker) loadCoreMod.invoke(null, Launch.classLoader, coreMod, file);
        ((List<ITweaker>) Launch.blackboard.get("Tweaks")).add(tweaker);
    }

    private void addMixinTransformerExclusion(String name) {
        if (relaunchInfo.loadedIds.contains("mixin")) {
            addMixinTransformerExclusionImpl(name);
        }
    }

    // Separate method because mixin classes are only available if mixin is loaded
    private static void addMixinTransformerExclusionImpl(String name) {
        ITransformerProvider transformers = MixinService.getService().getTransformerProvider();
        if (transformers != null) {
            transformers.addTransformerExclusion(name);
        }
    }

    private static class SourceFile {
        final File file;
        final String tweaker;
        final String coreMod;
        final boolean mixin;

        boolean initialized;

        private SourceFile(File file, String tweaker, String coreMod, boolean mixin) {
            this.file = file;
            this.tweaker = tweaker;
            this.coreMod = coreMod;
            this.mixin = mixin;
        }

        public static SourceFile readInfo(File file) throws IOException {
            String tweakClass = null;
            String coreMod = null;
            boolean mixin = false;
            try (JarFile jar = new JarFile(file)) {
                if (jar.getManifest() != null) {
                    Attributes attributes = jar.getManifest().getMainAttributes();
                    tweakClass = attributes.getValue("TweakClass");
                    coreMod = attributes.getValue("FMLCorePlugin");
                    mixin = attributes.getValue("MixinConfigs") != null;
                }
            }
            return new SourceFile(file, tweakClass, coreMod, mixin);
        }

        public static List<SourceFile> readInfos(Collection<URL> urls) {
            List<SourceFile> sourceFiles = new ArrayList<>();
            for (URL url : urls) {
                try {
                    URI uri = url.toURI();
                    if (!"file".equals(uri.getScheme())) {
                        continue;
                    }
                    File file = new File(uri);
                    if (!file.exists() || !file.isFile()) {
                        continue;
                    }
                    sourceFiles.add(readInfo(file));
                } catch (Exception e) {
                    LOGGER.error("Failed to read manifest from " + url + ":", e);
                }
            }
            return sourceFiles;
        }
    }
}
