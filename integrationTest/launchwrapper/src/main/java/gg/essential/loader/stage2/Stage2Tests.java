package gg.essential.loader.stage2;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static gg.essential.loader.fixtures.BaseInstallation.withBranch;
import static gg.essential.loader.util.Props.props;
import static gg.essential.loader.util.Props.writeProps;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertTrue(isolatedLaunch.getClass("sun.gg.essential.LoadState").getDeclaredField("dummyTweaker").getBoolean(null));

        String expectedHash = "bff7d3997b96b24f618ea906371bae01";
        assertEquals(expectedHash, md5Hex(Files.readAllBytes(installation.stage3DummyJarFile)));
        assertEquals(expectedHash, md5Hex(Files.readAllBytes(installation.essentialDir.resolve("Essential (forge_1.8.8).jar"))));
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
    public void testBranchSpecifiedInConfigFile(Installation installation) throws Exception {
        installation.addExampleMod();

        Path folder = installation.gameDir.resolve("essential");
        Files.createDirectories(folder);
        Files.write(folder.resolve("essential-loader.properties"), "branch=dummy".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.getClass("sun.gg.essential.LoadState").getDeclaredField("dummyTweaker").getBoolean(null));
    }

    @Test
    public void testUpdateRequiringNewerStage2(Installation installation) throws Exception {
        // First install at version 2
        installation.addExampleMod("bundled-2");
        Files.copy(withBranch(installation.stage3Meta, "2"), installation.stage3Meta, REPLACE_EXISTING);
        IsolatedLaunch firstLaunch = installation.launchFML();
        assertEquals("2", firstLaunch.getProperty("essential.stage2.version"));
        assertEquals("2", firstLaunch.getProperty("essential.version"));

        // Then try to upgrade Essential to version 4 which requires stage2 version 4
        Files.copy(withBranch(installation.stage3Meta, "4"), installation.stage3Meta, REPLACE_EXISTING);
        writeProps(installation.stage2ConfigFile, props("pendingUpdateVersion=4", "pendingUpdateResolution=true"));
        IsolatedLaunch secondLaunch = installation.launchFML();
        assertEquals("2", secondLaunch.getProperty("essential.stage2.version"));
        assertNull(secondLaunch.getProperty("essential.version"));
        assertThrows(ClassNotFoundException.class, () -> secondLaunch.getClass("sun.gg.essential.LoadState"));

        // Make available a newer stage2 version
        // We make available version 5 even though stage3 only requires version 4; we expect it to upgrade straight to 5
        Files.copy(withBranch(installation.stage2Meta, "5"), installation.stage2Meta, REPLACE_EXISTING);

        // Restart to complete upgrade
        IsolatedLaunch thirdLaunch = installation.launchFML();
        assertEquals("5", thirdLaunch.getProperty("essential.stage2.version"));
        assertEquals("4", thirdLaunch.getProperty("essential.version"));
    }
}
