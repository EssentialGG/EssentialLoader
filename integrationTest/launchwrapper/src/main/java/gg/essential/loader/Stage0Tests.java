package gg.essential.loader;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

public class Stage0Tests {
    @Test
    public void testUpdateFromFile(Installation installation) throws Exception {
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

    @Test
    public void testUpdateFromClasspath(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFML();

        installation.addExample2Mod();
        try (FileSystem fileSystem = FileSystems.newFileSystem(installation.modsDir.resolve("example2mod.jar"), null)) {
            Files.copy(
                installation.stage1Dummy.resolveSibling("forge_1-8-8.jar"),
                fileSystem.getPath("/gg/essential/loader/stage0/stage1.jar"),
                StandardCopyOption.REPLACE_EXISTING
            );
        }
        IsolatedLaunch isolatedLaunch = installation.launchFML();

        assertTrue(isolatedLaunch.getModLoadState("tweaker"), "Example Tweaker ran");
        assertFalse(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertFalse(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getClass("gg.essential.loader.stage1.EssentialSetupTweaker").getDeclaredField("ran").getBoolean(null));
    }
}
