package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.SecureJar;

import java.util.jar.Manifest;

public class ML9CompatibilityLayer implements CompatibilityLayer {
    @Override
    public Manifest getManifest(SecureJar jar) {
        return jar.getManifest();
    }

    @Override
    public EssentialModLocator makeModLocator() {
        return new ML9ModLocator();
    }
}
