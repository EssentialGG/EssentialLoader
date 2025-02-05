package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.SecureJar;

import java.util.List;

public interface EssentialModLocator {
    boolean injectMods(List<SecureJar> modJars) throws ReflectiveOperationException;
}
