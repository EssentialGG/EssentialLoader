package gg.essential.loader.stage1;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.CoreModManager;

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
    private final ITweaker stage0;
    private final EssentialLoader loader;

    public EssentialSetupTweaker(ITweaker stage0) throws Exception {
        this.stage0 = stage0;

        final Forge forge = Forge.getIfPresent();
        final Unknown unknown = new Unknown.Impl();
        final Platform platform = forge != null ? forge : unknown;

        platform.setupPreLoad(this);

        this.loader = EssentialLoader.getInstance(platform.getVersion());
        this.loader.load();

        platform.setupPostLoad(this);
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
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
                String coreMod = sourceFile.coreMod;
                if (coreMod != null) {
                    Method loadCoreMod = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
                    loadCoreMod.setAccessible(true);
                    ITweaker tweaker = (ITweaker) loadCoreMod.invoke(null, Launch.classLoader, coreMod, sourceFile.file);
                    ((List<ITweaker>) Launch.blackboard.get("Tweaks")).add(tweaker);
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
                        try (JarFile jar = new JarFile(file)) {
                            if (jar.getManifest() != null) {
                                Attributes attributes = jar.getManifest().getMainAttributes();
                                tweakClass = attributes.getValue("TweakClass");
                                coreMod = attributes.getValue("FMLCorePlugin");
                            }
                        }
                        if (tweakerClassName.equals(tweakClass)) {
                            sourceFiles.add(new SourceFile(file, coreMod));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return sourceFiles;
            }

            private static class SourceFile {
                final File file;
                final String coreMod;

                private SourceFile(File file, String coreMod) {
                    this.file = file;
                    this.coreMod = coreMod;
                }
            }
        }
    }
}
