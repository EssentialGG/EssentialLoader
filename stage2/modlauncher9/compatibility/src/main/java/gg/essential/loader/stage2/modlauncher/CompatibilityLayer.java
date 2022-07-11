package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.SecureJar;

import java.util.jar.Manifest;

/**
 * Provides abstractions for things which changed after ModLauncher 9.
 */
public interface CompatibilityLayer {
    Manifest getManifest(SecureJar jar);

    EssentialModLocator makeModLocator();
}
