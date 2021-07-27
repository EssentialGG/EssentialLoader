package gg.essential.impl;

import gg.essential.LoadState;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Mod;

@Mod(modid = "essential")
public class EssentialMod {
    {
        if (getClass().getClassLoader() != Launch.classLoader) {
            throw new IllegalStateException("Mod must be loaded via Launch class loader.");
        }

        if (!LoadState.tweaker) {
            throw new IllegalStateException("Tweaker failed to load");
        }

        LoadState.mod = true;
    }
}
