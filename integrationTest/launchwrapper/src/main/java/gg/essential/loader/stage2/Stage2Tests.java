package gg.essential.loader.stage2;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage2Tests {
    @Test
    public void testUpdateViaFullDownload(Installation installation) throws Exception {
        testUpdate(installation, false);
    }

    @Test
    public void testUpdateViaDiff(Installation installation) throws Exception {
        testUpdate(installation, true);
    }

    public void testUpdate(Installation installation, boolean viaDiff) throws Exception {
        installation.addExampleMod();

        installation.launchFML();

        Files.delete(installation.stage3Meta);
        Files.copy(installation.stage3DummyMeta, installation.stage3Meta);

        if (viaDiff) {
            // Prevent it from using the full download path
            Files.delete(installation.stage3DummyMetaDownload);
        } else {
            // Prevent it from using the diff download path
            Files.delete(installation.stage3DummyMetaDiff);
        }

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.getClass("gg.essential.api.tweaker.EssentialTweaker").getDeclaredField("dummyInitialized").getBoolean(null));
    }

    @Test
    public void testRawEssentialInModsFolder(Installation installation) throws Exception {
        installation.addExampleMod();
        Files.copy(installation.stage3JarFile, installation.modsDir.resolve("essential.jar"));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
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
            installation.launchFML();
        }

        Files.write(installation.stage3Meta, new byte[0]);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testUpdateWithBundledVersion(Installation installation) throws Exception {
        installation.addExampleMod("bundled");

        installation.launchFML();

        // Enable auto-update via config file (otherwise we won't even check when there's a pinned file present)
        Files.write(installation.gameDir.resolve("essential").resolve("essential-loader.properties"),
            "autoUpdate=true".getBytes(StandardCharsets.UTF_8));

        Files.delete(installation.stage3Meta);
        Files.copy(installation.stage3DummyMeta, installation.stage3Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.getClass("gg.essential.api.tweaker.EssentialTweaker").getDeclaredField("dummyInitialized").getBoolean(null));
    }

    @Test
    public void testBranchSpecifiedInConfigFile(Installation installation) throws Exception {
        installation.addExampleMod();

        Path folder = installation.gameDir.resolve("essential");
        Files.createDirectories(folder);
        Files.write(folder.resolve("essential-loader.properties"), "branch=dummy".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.getClass("gg.essential.api.tweaker.EssentialTweaker").getDeclaredField("dummyInitialized").getBoolean(null));
    }
}
