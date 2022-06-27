package gg.essential.loader.stage2;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Stage0TweakClassDetectionTests {
    private static final String KEY = "essential.loader.stage2.stage0tweakers";

    @Test
    public void testDetectionWithEssentialTweaker(Installation installation) throws Exception {
        installation.addExampleMod("essential-tweaker");
        installation.addExample2Mod("essential-tweaker");

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertEquals(setOf(
            "gg.essential.loader.stage0.EssentialSetupTweaker"
        ), isolatedLaunch.getBlackboard().get(KEY));
    }

    @Test
    public void testDetectionWithCustomTweakers(Installation installation) throws Exception {
        installation.addExampleMod();
        installation.addExample2Mod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertEquals(setOf(
            "gg.essential.loader.stage0.EssentialSetupTweaker"
        ), isolatedLaunch.getBlackboard().get(KEY));
    }

    @Test
    public void testDetectionOfRelocatedTweaker(Installation installation) throws Exception {
        installation.addExampleMod("relocated");
        installation.addExample2Mod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertEquals(setOf(
            "gg.essential.loader.stage0.EssentialSetupTweaker",
            "com.example.mod.essential.stage0.EssentialSetupTweaker"
        ), isolatedLaunch.getBlackboard().get(KEY));
    }

    private static Set<String> setOf(String...args) {
        return new HashSet<>(Arrays.asList(args));
    }
}
