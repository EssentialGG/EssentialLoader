package gg.essential.api.tweaker;

import gg.essential.LoadState;
import net.minecraft.launchwrapper.Launch;

import java.io.File;

@SuppressWarnings("unused")
public class EssentialTweaker {
    public static void initialize(File gameDir) {
        LoadState.tweaker = true;

        ClassLoader expectedLoader = Launch.classLoader.getClass().getClassLoader();
        ClassLoader actualLoader = EssentialTweaker.class.getClassLoader();
        if (expectedLoader != actualLoader) {
            throw new RuntimeException("Essential should be excluded from the Launch class loader." +
                "Expected: " + expectedLoader + ", but was " + actualLoader);
        }
    }
}
