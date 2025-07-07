package gg.essential.loader.stage1;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1TweakerTests {
    @Test
    public void testMultipleCustomTweakerMods(Installation installation) throws Exception {
        installation.addExampleMod();
        installation.addExample2Mod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        installation.assertMod2Launched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testMultipleEssentialTweakerMods(Installation installation) throws Exception {
        installation.addExampleMod("essential-tweaker");
        installation.addExample2Mod("essential-tweaker");

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("coreMod"), "Example2 CoreMod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mod"), "Example2 Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
