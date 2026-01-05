package gg.essential.loader.stage2;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * This class (and jar more generally) exists to be loaded by the auto-update mechanism of an old stage1.
 * It is meant to be uploaded to Essential's infra where it can be downloaded by such old stage1s.
 * It contains the real stage2 embedded inside it as a jar file, and when executed simply extracts that and then jumps
 * to its `EssentialLoaderTweaker` class as a newer stage1 would do.
 *
 * Having this wrapper is necessary because old stage1 will add the downloaded jar to the launch and system class
 * loader before executing it, which prevents the stage2 self-update mechanism from working because if it tries to
 * create a new ClassLoader for the newer stage2 it found and tries to load it, that'll just return the old stage2
 * that's already available in the parent class loader.
 * That's why we have this wrapper, so only this wrapper class becomes un-updatable, but the real stage2 is loaded in a
 * separate ClassLoader and can therefore load a newer version of itself in another separate ClassLoader.
 */
@Deprecated // called by old stage1
@SuppressWarnings("unused")
public class EssentialLoader {
    public EssentialLoader(Path gameDir, String gameVersion) throws Exception {
        Path extracted = Files.createTempFile("essential-loader-stage2", ".jar");
        try (InputStream in = EssentialLoader.class.getResourceAsStream("real-stage2.jar")) {
            assert(in != null);
            Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
        }
        URLClassLoader classLoader = new URLClassLoader(new URL[]{extracted.toUri().toURL()}, Launch.classLoader);

        Class<?> cls = classLoader.loadClass("gg.essential.loader.stage2.EssentialSetupTweaker");
        cls.getConstructor(ITweaker.class).newInstance((ITweaker) null);

        throw new AssertionError("should relaunch and never return");
    }
}
