package gg.essential.loader.stage1;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.List;

@SuppressWarnings("unused")
public class EssentialSetupTweaker implements ITweaker {
    public static boolean ran;

    public EssentialSetupTweaker(ITweaker stage0) throws URISyntaxException, IOException {
        File stage0File = getSourceFile(stage0.getClass());
        File stage1File = getSourceFile(getClass());
        if (stage0File.getCanonicalFile().equals(stage1File.getCanonicalFile())) {
            throw new RuntimeException("Stage0/1 should be in separate files.\nStage0: " + stage0File + "\nStage1: " + stage1File);
        }

        ClassLoader expectedLoader = Launch.classLoader.getClass().getClassLoader();
        ClassLoader actualLoader = getClass().getClassLoader();
        if (expectedLoader != actualLoader) {
            throw new RuntimeException("Stage1 should be excluded from the Launch class loader." +
                "Expected: " + expectedLoader + ", but was " + actualLoader);
        }
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        ran = true;
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    private File getSourceFile(Class<?> cls) throws URISyntaxException {
        CodeSource codeSource = cls.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL location = codeSource.getLocation();
            return new File(location.toURI());
        }
        throw new RuntimeException("Failed to get file for " + cls.getName());
    }
}
