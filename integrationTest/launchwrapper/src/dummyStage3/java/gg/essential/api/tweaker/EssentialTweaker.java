package gg.essential.api.tweaker;

import java.io.File;

@SuppressWarnings("unused")
public class EssentialTweaker {
    public static boolean dummyInitialized;

    public static void initialize(File gameDir) {
        dummyInitialized = true;
    }
}
