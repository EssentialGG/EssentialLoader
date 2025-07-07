package gg.essential.api.tweaker;

import sun.gg.essential.LoadState;

import java.io.File;

@SuppressWarnings("unused")
public class EssentialTweaker {
    public static void initialize(File gameDir) {
        LoadState.tweaker = true;
    }
}
