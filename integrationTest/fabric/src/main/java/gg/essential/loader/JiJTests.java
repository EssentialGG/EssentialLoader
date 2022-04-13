package gg.essential.loader;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import gg.essential.loader.util.Delete;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JiJTests {
    @Test
    public void testWithoutThirdParty(Installation installation) throws Exception {
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("2", isolatedLaunch.getModVersion("jij"));

        isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij3");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("3", isolatedLaunch.getModVersion("jij"));
    }

    @Test
    public void testDeeplyNestedWithoutThirdParty(Installation installation) throws Exception {
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jijij");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("1", isolatedLaunch.getModVersion("jijij"));
        assertEquals("1", isolatedLaunch.getModVersion("jij"));
    }

    @Test
    public void testInstallationWithOldModPresent(Installation installation) throws Exception {
        installation.addExampleMod();
        installation.addJijMod("1");

        IsolatedLaunch isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("1", isolatedLaunch.getModVersion("jij"));

        isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("2", isolatedLaunch.getModVersion("jij"));
    }

    @Test
    public void testInstallationWithNewModPresent(Installation installation) throws Exception {
        installation.addExampleMod();
        installation.addJijMod("3");

        IsolatedLaunch isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("3", isolatedLaunch.getModVersion("jij"));
    }

    @Test
    public void testUpgradeWithOldModPresent(Installation installation) throws Exception {
        installation.addExampleMod();
        installation.addJijMod("1");

        IsolatedLaunch isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("1", isolatedLaunch.getModVersion("jij"));

        isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij3");
        isolatedLaunch.launch();

        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("2", isolatedLaunch.getModVersion("jij"));

        isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij3");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("3", isolatedLaunch.getModVersion("jij"));
    }

    @Test
    public void testUpgradeWithNewModPresent(Installation installation) throws Exception {
        // Make it write version 2 to our dependencies jar
        installation.addExampleMod();
        installation.addJijMod("1");

        IsolatedLaunch isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        // Then upgrade to 3 while a mod with 3 is already present
        Delete.contentsRecursively(installation.modsDir);
        installation.addExampleMod();
        installation.addJijMod("3");

        isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij3");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("3", isolatedLaunch.getModVersion("jij"));
    }

    @Test
    public void testThirdPartyDowngrade(Installation installation) throws Exception {
        // Start normally
        installation.addExampleMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        // Install third-party mod which downgrades to version 1
        installation.addJijMod("1");

        isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("1", isolatedLaunch.getModVersion("jij"));

        // Should be functional after one restart
        isolatedLaunch = installation.newLaunchFabric();
        isolatedLaunch.setProperty("essential.branch", "jij2");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertEquals("2", isolatedLaunch.getModVersion("jij"));
    }
}
