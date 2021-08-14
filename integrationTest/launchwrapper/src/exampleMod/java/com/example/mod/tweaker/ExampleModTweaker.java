package com.example.mod.tweaker;

import net.minecraft.launchwrapper.Launch;
import sun.com.example.mod.LoadState;
import gg.essential.loader.stage0.EssentialSetupTweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class ExampleModTweaker extends EssentialSetupTweaker {
    static {
        if (Boolean.parseBoolean(System.getProperty("examplemod.exclude_kotlin_from_transformers", "false"))) {
            Launch.classLoader.addTransformerExclusion("kotlin.something.");
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        super.injectIntoClassLoader(classLoader);
        LoadState.tweaker = true;
    }
}
