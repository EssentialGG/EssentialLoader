package net.minecraft.client;

import net.fabricmc.loader.api.FabricLoader;

public class Util {
    @SuppressWarnings("unused") // called from IsolatedLaunch.getModVersion
    public static String getModVersion(String modId) {
        return FabricLoader.getInstance()
            .getModContainer(modId)
            .map(it -> it.getMetadata().getVersion().toString())
            .orElse(null);
    }
}
