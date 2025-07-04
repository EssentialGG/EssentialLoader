package gg.essential.loader.stage1;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@SuppressWarnings("unused")
public class EssentialSetupTweaker implements ITweaker {
    private final ITweaker stage0;
    private final ITweaker stage2;

    private static Class<?> stage2Cls;
    private static synchronized ITweaker newStage2Tweaker(ITweaker stage0) throws Exception {
        if (stage2Cls == null) {
            Path extracted = Files.createTempFile("essential-loader-stage2-", ".jar");
            extracted.toFile().deleteOnExit();
            try (InputStream in = EssentialSetupTweaker.class.getResourceAsStream("stage2.jar")) {
                assert(in != null);
                Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
            }
            URLClassLoader classLoader = new URLClassLoader(new URL[]{extracted.toUri().toURL()}, Launch.classLoader);

            stage2Cls = classLoader.loadClass("gg.essential.loader.stage2.EssentialSetupTweaker");
        }
        return (ITweaker) stage2Cls.getConstructor(ITweaker.class).newInstance(stage0);
    }

    public EssentialSetupTweaker(ITweaker stage0) throws Exception {
        this.stage0 = stage0;

        if (DelayedStage0Tweaker.isRequired()) {
            DelayedStage0Tweaker.prepare(stage0);
            this.stage2 = null;
            return;
        }

        this.stage2 = newStage2Tweaker(stage0);
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        if (this.stage2 != null) {
            this.stage2.acceptOptions(args, gameDir, assetsDir, profile);
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        if (this.stage2 == null) {
            DelayedStage0Tweaker.inject();
            return;
        }
        this.stage2.injectIntoClassLoader(classLoader);
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
