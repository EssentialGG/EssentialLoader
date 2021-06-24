package com.example.mod;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Mod;

@Mod(modid = "examplemod")
public class ExampleMod {
    {
        if (getClass().getClassLoader() != Launch.classLoader) {
            throw new IllegalStateException("Mod must be loaded via Launch class loader.");
        }
        LoadState.mod = true;
    }
}
