package gg.essential.loader.stage1;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1MixinTests {
    @Test
    public void testMultipleCustomTweakerModsWithMixin07(Installation installation) throws Exception {
        testMultipleCustomTweakerModsWithMixin(installation, "07");
    }

    @Test
    public void testMultipleCustomTweakerModsWithMixin08(Installation installation) throws Exception {
        testMultipleCustomTweakerModsWithMixin(installation, "08");
    }

    public void testMultipleCustomTweakerModsWithMixin(Installation installation, String mixinVersion) throws Exception {
        installation.addExampleMod("stable-with-mixin-" + mixinVersion);
        installation.addExample2Mod("stable-with-mixin-" + mixinVersion);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        installation.assertMod2Launched(isolatedLaunch);
        assertTrue(isolatedLaunch.getModLoadState("mixin"), "Example mixin plugin ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mixin"), "Example2 mixin plugin ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testMultipleEssentialTweakerModsWithMixin07(Installation installation) throws Exception {
        testMultipleEssentialTweakerModsWithMixin(installation, "07");
    }

    @Test
    public void testMultipleEssentialTweakerModsWithMixin08(Installation installation) throws Exception {
        testMultipleEssentialTweakerModsWithMixin(installation, "08");
    }

    public void testMultipleEssentialTweakerModsWithMixin(Installation installation, String mixinVersion) throws Exception {
        installation.addExampleMod("essential-tweaker-with-mixin-" + mixinVersion);
        installation.addExample2Mod("essential-tweaker-with-mixin-" + mixinVersion);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.getModLoadState("mixin"), "Example mixin plugin ran");
        assertTrue(isolatedLaunch.getMod2LoadState("coreMod"), "Example2 CoreMod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mod"), "Example2 Mod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mixin"), "Example2 mixin plugin ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testWithThirdPartyQueuedMixin07(Installation installation) throws Exception {
        testWithThirdPartyQueuedMixin(installation, "07");
    }

    @Test
    public void testWithThirdPartyQueuedMixin08(Installation installation) throws Exception {
        testWithThirdPartyQueuedMixin(installation, "08");
    }

    public void testWithThirdPartyQueuedMixin(Installation installation, String mixinVersion) throws Exception {
        installation.addExampleMod("mixin-tweaker-with-mixin-" + mixinVersion);
        installation.addExample2Mod("essential-tweaker-with-mixin-" + mixinVersion);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.getModLoadState("mixin"), "Example mixin plugin ran");
        assertTrue(isolatedLaunch.getMod2LoadState("coreMod"), "Example2 CoreMod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mod"), "Example2 Mod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mixin"), "Example2 mixin plugin ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
