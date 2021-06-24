package gg.essential.api.tweaker;

import net.minecraft.launchwrapper.Launch;

import java.io.File;

@SuppressWarnings("unused")
public class EssentialTweaker {
    public static boolean loaded;

    public static void initialize(File gameDir) {
        loaded = true;

        ClassLoader expectedLoader = Launch.classLoader.getClass().getClassLoader();
        ClassLoader actualLoader = EssentialTweaker.class.getClassLoader();
        if (expectedLoader != actualLoader) {
            throw new RuntimeException("Essential should be excluded from the Launch class loader." +
                "Expected: " + expectedLoader + ", but was " + actualLoader);
        }
    }
}
