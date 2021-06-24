package com.example.mod2.tweaker;

import com.example.mod2.LoadState;
import gg.essential.loader.stage0.EssentialSetupTweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class ExampleModTweaker extends EssentialSetupTweaker {
    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        super.injectIntoClassLoader(classLoader);
        LoadState.tweaker = true;
    }
}
