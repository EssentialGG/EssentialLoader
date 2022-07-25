package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.SecureJar;

import java.util.jar.Manifest;

public class ML10CompatibilityLayer implements CompatibilityLayer {
    @Override
    public Manifest getManifest(SecureJar jar) {
        return jar.moduleDataProvider().getManifest();
    }

    @Override
    public EssentialModLocator makeModLocator() {
        return new Forge_41_0_34_ModLocator();
    }
}
