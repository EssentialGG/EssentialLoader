package gg.essential.loader.stage1;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1DevEnvTests {
    private IsolatedLaunch newDevLaunch(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.addToClasspath(installation.stage0JarFile.toUri().toURL());
        isolatedLaunch.addToClasspath(installation.mixin07JarFile.toUri().toURL());
        isolatedLaunch.addToClasspath(Paths.get("build", "classes", "java", "exampleMod").toUri().toURL());
        isolatedLaunch.addArg("--tweakClass", "gg.essential.loader.stage0.EssentialSetupTweaker");
        isolatedLaunch.setProperty("fml.coreMods.load", "com.example.mod.ExampleCoreMod");
        isolatedLaunch.setProperty("essential.loader.installEssentialMod", "true");
        return isolatedLaunch;
    }

    @Test
    public void testInDev(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = newDevLaunch(installation);
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testEssentialTweakerModsInDev(Installation installation) throws Exception {
        installation.addExample2Mod("essential-tweaker");

        IsolatedLaunch isolatedLaunch = newDevLaunch(installation);
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("coreMod"), "Example2 CoreMod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mod"), "Example2 Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
