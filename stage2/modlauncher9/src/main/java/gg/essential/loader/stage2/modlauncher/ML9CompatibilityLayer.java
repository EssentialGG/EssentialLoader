package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.SecureJar;

import java.util.jar.Manifest;

import static gg.essential.loader.stage2.util.Utils.hasClass;

public class ML9CompatibilityLayer implements CompatibilityLayer {
    @Override
    public Manifest getManifest(SecureJar jar) {
        return jar.getManifest();
    }

    @Override
    public EssentialModLocator makeModLocator() {
        String version;
        if (hasClass("net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator")) {
            version = "40_1_60";
        } else {
            version = "37_0_0";
        }
        try {
            String clsName = "gg.essential.loader.stage2.modlauncher.Forge_" + version + "_ModLocator";
            return (EssentialModLocator) Class.forName(clsName)
                .getDeclaredConstructor()
                .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
