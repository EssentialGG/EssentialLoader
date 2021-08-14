package gg.essential.impl;

import sun.gg.essential.LoadState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public class EssentialMod implements ClientModInitializer {
    public static boolean mixinWorking;

    @Override
    public void onInitializeClient() {
        if (!mixinWorking) {
            throw new IllegalStateException("Mixin is not working.");
        }

        FabricLoader fabricLoader = FabricLoader.getInstance();

        ModContainer modContainer = fabricLoader.getModContainer("essential")
            .orElseThrow(() -> new IllegalStateException("Missing fabric mod"));
        if (!modContainer.getMetadata().containsCustomValue("test")) {
            throw new IllegalStateException("Missing custom value");
        }

        LoadState.mod = true;
    }
}
