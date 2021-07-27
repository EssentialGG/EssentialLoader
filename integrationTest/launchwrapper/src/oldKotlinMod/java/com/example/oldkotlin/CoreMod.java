package com.example.oldkotlin;

import kotlin.EarlyLoadedKotlin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

public class CoreMod implements IFMLLoadingPlugin {
    static {
        // This one we should be able to load early but still be fine because the method still exists in the new version
        EarlyLoadedKotlin.assertCorrectVersionPresent();
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
    public void injectData(Map<String, Object> map) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
