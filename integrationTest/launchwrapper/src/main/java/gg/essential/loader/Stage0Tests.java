package gg.essential.loader;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class Stage0Tests {
    @Test
    public void testUpdate() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        installation.launchFML();

        Files.copy(
            installation.stage1Dummy.resolveSibling("forge_1-8-8.jar"),
            installation.gameDir.resolve("essential/loader/stage0/launchwrapper/stage1.update.jar")
        );
        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertTrue(isolatedLaunch.getModLoadState("tweaker"), "Example Tweaker ran");
        assertFalse(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertFalse(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getClass("gg.essential.loader.stage1.EssentialSetupTweaker").getDeclaredField("ran").getBoolean(null));
    }
}
