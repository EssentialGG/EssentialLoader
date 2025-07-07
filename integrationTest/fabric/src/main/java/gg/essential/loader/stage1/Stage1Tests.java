package gg.essential.loader.stage1;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1Tests {
    @Test
    public void testUpdate(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFabric();

        Files.delete(installation.stage2Meta);
        Files.copy(installation.stage2DummyMeta, installation.stage2Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getEssentialLoadState("dummyStage2Loaded"));
        assertTrue(isolatedLaunch.getEssentialLoadState("dummyStage2Initialized"));
    }

    @Test
    public void testUnsupportedVersionOnFirstLaunch(Installation installation) throws Exception {
        testUnsupportedVersion(installation, false);
    }

    @Test
    public void testUnsupportedVersionOnSecondLaunch(Installation installation) throws Exception {
        testUnsupportedVersion(installation, true);
    }

    public void testUnsupportedVersion(Installation installation, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFabric();
        }

        Files.write(installation.stage2Meta, new byte[0]);

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testBranchSpecifiedInConfigFile(Installation installation) throws Exception {
        installation.addExampleMod();

        Files.createDirectories(installation.stage1ConfigFile.getParent());
        Files.write(installation.stage1ConfigFile, "branch=dummy".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getEssentialLoadState("dummyStage2Loaded"));
        assertTrue(isolatedLaunch.getEssentialLoadState("dummyStage2Initialized"));
    }
}
