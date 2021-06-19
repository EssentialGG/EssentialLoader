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
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.List;
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

            @SuppressWarnings("unchecked")
            @Override
            public void setupPostLoad(EssentialSetupTweaker stage1) throws Exception {
                final File sourceFile = getSourceFile(stage1.stage0.getClass());
                if (sourceFile == null) {
                    System.out.println("Not able to determine current file. Mod will NOT work");
                    return;
                }

                // Forge will by default ignore a mod file if it contains a tweaker
                // So we need to remove ourselves from that exclusion list
                Field ignoredModFile = CoreModManager.class.getDeclaredField("ignoredModFiles");
                ignoredModFile.setAccessible(true);
                ((List<String>) ignoredModFile.get(null)).remove(sourceFile.getName());

                // And instead add ourselves to the mod candidate list
                CoreModManager.getReparseableCoremods().add(sourceFile.getName());

                // FML will not load CoreMods if it finds a tweaker, so we need to load the coremod manually if present
                // We do this to reduce the friction of adding our tweaker if a mod has previously been relying on a
                // coremod (cause ordinarily they would have to convert their coremod into a tweaker manually).
                String coreMod = getCoreMod(sourceFile);
                if (coreMod != null) {
                    Method loadCoreMod = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
                    loadCoreMod.setAccessible(true);
                    ITweaker tweaker = (ITweaker) loadCoreMod.invoke(null, Launch.classLoader, coreMod, sourceFile);
                    ((List<ITweaker>) Launch.blackboard.get("Tweaks")).add(tweaker);
                }
            }

            private File getSourceFile(Class<?> cls) throws URISyntaxException {
                CodeSource codeSource = cls.getProtectionDomain().getCodeSource();
                if (codeSource != null) {
                    URL location = codeSource.getLocation();
                    return new File(location.toURI());
                }
                return null;
            }

            private String getCoreMod(File file) throws IOException {
                try (JarFile jar = new JarFile(file)) {
                    if (jar.getManifest() != null) {
                        return jar.getManifest().getMainAttributes().getValue("FMLCorePlugin");
                    }
                }
                return null;
            }
        }
    }
}
