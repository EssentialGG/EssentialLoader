package gg.essential.loader;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.CoreModManager;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.List;

@SuppressWarnings("unused")
public class EssentialSetupTweaker implements ITweaker {
    private final EssentialLoader loader;

    public EssentialSetupTweaker() {
        this(new String[0]);
    }

    public EssentialSetupTweaker(String[] fmlPlugins) {
        String version = "unknown";

        try {
            version = "forge_" + ForgeVersion.class.getDeclaredField("mcVersion").get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        this.loader = new EssentialLoader(Launch.minecraftHome, version);
        loader.load();

        try {
            Field ignored = CoreModManager.class.getDeclaredField("ignoredModFiles");
            ignored.setAccessible(true);
            final File currentFile = getCurrentFile();
            if (currentFile == null) {
                System.out.println("Not able to determine current file. Mod will NOT work");
                return;
            }
            ((List<String>) ignored.get(null)).remove(currentFile.getName());

            CoreModManager.getReparseableCoremods().add(currentFile.getName());

            Method loadCoreMod = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
            loadCoreMod.setAccessible(true);
            for (String s : fmlPlugins) {
                ((List<ITweaker>) Launch.blackboard.get("Tweaks")).add((ITweaker) loadCoreMod.invoke(null, Launch.classLoader, s, currentFile));

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getCurrentFile() throws URISyntaxException {
        CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL location = codeSource.getLocation();
            return new File(location.toURI());
        }
        return null;
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        loader.initializeEssential();
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
