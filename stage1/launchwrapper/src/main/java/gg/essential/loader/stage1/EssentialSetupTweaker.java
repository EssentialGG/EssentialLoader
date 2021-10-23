package gg.essential.loader.stage1;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

@SuppressWarnings("unused")
public class EssentialSetupTweaker implements ITweaker {
    private static final Logger LOGGER = LogManager.getLogger(EssentialSetupTweaker.class);
    private final ITweaker stage0;
    private final EssentialLoader loader;

    public EssentialSetupTweaker(ITweaker stage0) throws Exception {
        this.stage0 = stage0;

        if (DelayedStage0Tweaker.isRequired()) {
            DelayedStage0Tweaker.prepare(stage0);
            this.loader = null;
            return;
        }

        final Forge forge = Forge.getIfPresent();
        final Unknown unknown = new Unknown.Impl();
        final Platform platform = forge != null ? forge : unknown;

        platform.setupPreLoad(this);

        this.loader = EssentialLoader.getInstance(platform.getVersion());
        this.loader.load(Launch.minecraftHome.toPath());

        platform.setupPostLoad(this);
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        if (this.loader == null) {
            DelayedStage0Tweaker.inject();
            return;
        }
        this.loader.initialize();
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    private interface Platform {
        String getVersion();
        default void setupPreLoad(EssentialSetupTweaker stage1) throws Exception {}
        default void setupPostLoad(EssentialSetupTweaker stage1) throws Exception {}
    }

    private interface Unknown extends Platform {
        class Impl implements Unknown {
            @Override
            public String getVersion() {
                return "unknown";
            }
        }
    }

    private interface Forge extends Platform {
        static Forge getIfPresent() throws IOException {
            if (Launch.classLoader.getClassBytes("net.minecraftforge.common.ForgeVersion") != null) {
                return getUnchecked();
            } else {
                return null;
            }
        }

        static Forge getUnchecked() {
            return new Impl();
        }

        class Impl implements Forge {
            private static final String MIXIN_TWEAKER = "org.spongepowered.asm.launch.MixinTweaker";

            @Override
            public String getVersion() {
                try {
                    // Accessing via reflection so the compiler does not inline the value at build time.
                    return "forge_" + ForgeVersion.class.getDeclaredField("mcVersion").get(null);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    e.printStackTrace();
                    return "unknown";
                }
            }

            @Override
            public void setupPostLoad(EssentialSetupTweaker stage1) throws Exception {
                final List<SourceFile> sourceFiles = getSourceFiles(stage1.stage0.getClass());
                if (sourceFiles.isEmpty()) {
                    System.out.println("Not able to determine current file. Mod will NOT work");
                    return;
                }
                for (SourceFile sourceFile : sourceFiles) {
                    setupSourceFile(sourceFile);
                }
            }

            @SuppressWarnings("unchecked")
            private void setupSourceFile(SourceFile sourceFile) throws Exception {
                // Forge will by default ignore a mod file if it contains a tweaker
                // So we need to remove ourselves from that exclusion list
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
                    Method loadCoreMod = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
                    loadCoreMod.setAccessible(true);
                    ITweaker tweaker = (ITweaker) loadCoreMod.invoke(null, Launch.classLoader, coreMod, sourceFile.file);
                    ((List<ITweaker>) Launch.blackboard.get("Tweaks")).add(tweaker);
                }

                // If they declared our tweaker but also want to use mixin, then we'll inject the mixin tweaker
                // for them.
                if (sourceFile.mixin) {
                    // Mixin will only look at jar files which declare the MixinTweaker as their tweaker class, so we need
                    // to manually add our source files for inspection.
                    try {
                        injectMixinTweaker();

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

            private List<SourceFile> getSourceFiles(Class<?> tweakerClass) {
                String tweakerClassName = tweakerClass.getName();
                List<SourceFile> sourceFiles = new ArrayList<>();
                for (URL url : Launch.classLoader.getSources()) {
                    try {
                        URI uri = url.toURI();
                        if (!"file".equals(uri.getScheme())) {
                            continue;
                        }
                        File file = new File(uri);
                        if (!file.exists() || !file.isFile()) {
                            continue;
                        }
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
                        if (tweakerClassName.equals(tweakClass)) {
                            sourceFiles.add(new SourceFile(file, coreMod, mixin));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to read manifest from " + url + ":", e);
                    }
                }
                return sourceFiles;
            }

            private void injectMixinTweaker() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
                @SuppressWarnings("unchecked")
                List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");

                // If the MixinTweaker is already queued (because of another mod), then there's nothing we need to to
                if (tweakClasses.contains(MIXIN_TWEAKER)) {
                    // Except we do need to initialize the MixinTweaker immediately so we can add containers
                    // for our mods.
                    // This is idempotent, so we can call it without adding to the tweaks list (and we must not add to
                    // it because the queued tweaker will already get added and there is nothing we can do about that).
                    initMixinTweaker();
                    return;
                }

                // If it is already booted, we're also good to go
                if (Launch.blackboard.get("mixin.initialised") != null) {
                    return;
                }

                System.out.println("Injecting MixinTweaker from EssentialSetupTweaker");

                // Otherwise, we need to take things into our own hands because the normal way to chainload a tweaker
                // (by adding it to the TweakClasses list during injectIntoClassLoader) is too late for Mixin.
                // Instead we instantiate the MixinTweaker on our own and add it to the current Tweaks list immediately.
                @SuppressWarnings("unchecked")
                List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
                tweaks.add(initMixinTweaker());
            }

            private ITweaker initMixinTweaker() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
                Launch.classLoader.addClassLoaderExclusion(MIXIN_TWEAKER.substring(0, MIXIN_TWEAKER.lastIndexOf('.')));
                return (ITweaker) Class.forName(MIXIN_TWEAKER, true, Launch.classLoader).newInstance();
            }

            private static class SourceFile {
                final File file;
                final String coreMod;
                final boolean mixin;

                private SourceFile(File file, String coreMod, boolean mixin) {
                    this.file = file;
                    this.coreMod = coreMod;
                    this.mixin = mixin;
                }
            }
        }
    }
}
