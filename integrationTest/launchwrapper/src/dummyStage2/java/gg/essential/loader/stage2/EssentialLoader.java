package gg.essential.loader.stage2;

import net.minecraft.launchwrapper.Launch;

import java.nio.file.Path;

@SuppressWarnings("unused")
public class EssentialLoader {
    public static boolean loaded;
    public static boolean initialized;

    public EssentialLoader(Path gameDir, String gameVersion) {
        ClassLoader expectedLoader = Launch.classLoader.getClass().getClassLoader();
        ClassLoader actualLoader = getClass().getClassLoader();
        if (expectedLoader != actualLoader) {
            throw new RuntimeException("Stage2 should be excluded from the Launch class loader." +
                "Expected: " + expectedLoader + ", but was " + actualLoader);
        }
    }


    public void load() {
        loaded = true;
    }

    public void initialize() {
        initialized = true;
    }
}
