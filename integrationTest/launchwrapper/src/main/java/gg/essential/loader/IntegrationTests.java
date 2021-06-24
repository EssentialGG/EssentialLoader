package gg.essential.loader;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntegrationTests {
    @Test
    public void testFirstLaunch() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testSecondLaunch() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        installation.launchFML();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
