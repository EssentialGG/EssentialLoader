package gg.essential.loader;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelaunchTests {
    @Test
    public void testOldKotlinOnClasspath(Installation installation) throws Exception {
        installation.addExampleMod();
        installation.addOldKotlinMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.setProperty("essential.loader.relaunch", "false"); // do not want to re-launch for this one
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testRelaunchDueToOldKotlin(Installation installation) throws Exception {
        installation.addExampleMod();
        installation.addOldKotlinMod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testRelaunchDueToOldMixin(Installation installation) throws Exception {
        installation.addExampleMod("stable-with-mixin-07");

        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.setProperty("essential.branch", "mixin-08");
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testRelaunchDueToKotlinBeingExcluded(Installation installation) throws Exception {
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.setProperty("examplemod.exclude_kotlin_from_transformers", "true");
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testRelaunchDueToOldAsm(Installation installation) throws Exception {
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.setProperty("essential.branch", "asm-52");
        isolatedLaunch.setProperty("examplemod.require_asm52", "true");
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
