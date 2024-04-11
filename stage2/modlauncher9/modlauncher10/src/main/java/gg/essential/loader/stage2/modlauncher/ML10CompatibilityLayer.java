package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.SecureJar;

import java.util.jar.Manifest;

import static gg.essential.loader.stage2.Utils.hasClass;

public class ML10CompatibilityLayer implements CompatibilityLayer {
    @Override
    public Manifest getManifest(SecureJar jar) {
        return jar.moduleDataProvider().getManifest();
    }

    @Override
    public EssentialModLocator makeModLocator() {
        String version;
        if (!hasClass("net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator")) {
            version = "49_0_38";
        } else {
            version = "41_0_34";
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
