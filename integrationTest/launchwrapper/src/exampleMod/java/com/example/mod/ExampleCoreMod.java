package com.example.mod;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.Map;

public class ExampleCoreMod implements IFMLLoadingPlugin {
    {
        try {
            verifyMixinLoaded();
        } catch (NoClassDefFoundError ignored) { // only care about it in tests where we use mixin
        }

        if (getClass().getClassLoader() != Launch.classLoader) {
            throw new IllegalStateException("CoreMod must be loaded via Launch class loader.");
        }
        LoadState.coreMod = true;
    }

    private void verifyMixinLoaded() {
        MixinEnvironment.getDefaultEnvironment(); // verifies that mixin has been initialized properly
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
