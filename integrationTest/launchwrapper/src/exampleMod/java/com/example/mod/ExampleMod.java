package com.example.mod;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Mod;

import sun.com.example.mod.LoadState;

@Mod(modid = "examplemod")
public class ExampleMod {
    {
        if (getClass().getClassLoader() != Launch.classLoader) {
            throw new IllegalStateException("Mod must be loaded via Launch class loader.");
        }
        LoadState.checkForRelaunch();
        LoadState.mod = true;
    }
}
