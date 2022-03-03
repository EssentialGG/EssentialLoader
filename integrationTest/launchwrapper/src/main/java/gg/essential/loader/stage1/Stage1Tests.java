package gg.essential.loader.stage1;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1Tests {
    @Test
    public void testUpdate(Installation installation) throws Exception {
        installation.addExampleMod();

        installation.launchFML();

        Files.delete(installation.stage2Meta);
        Files.copy(installation.stage2DummyMeta, installation.stage2Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getClass("gg.essential.loader.stage2.EssentialLoader").getDeclaredField("loaded").getBoolean(null));
        assertTrue(isolatedLaunch.getClass("gg.essential.loader.stage2.EssentialLoader").getDeclaredField("initialized").getBoolean(null));
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

        Files.write(installation.stage2Meta, new byte[0]);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testJsonSyntaxInvalidFirstLaunch(Installation installation) throws Exception {
        testJsonSyntaxInvalid(installation, false);
    }

    @Test
    public void testJsonSyntaxInvalidOnSecondLaunch(Installation installation) throws Exception {
        testJsonSyntaxInvalid(installation, true);
    }

    public void testJsonSyntaxInvalid(Installation installation, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.write(installation.stage2Meta, "{ oh no }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testJsonContentInvalidOnFirstLaunch(Installation installation) throws Exception {
        testJsonContentInvalid(installation, false);
    }

    @Test
    public void testJsonContentInvalidOnSecondLaunch(Installation installation) throws Exception {
        testJsonContentInvalid(installation, true);
    }

    public void testJsonContentInvalid(Installation installation, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.write(installation.stage2Meta, "{ \"url\": 42 }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testServerErrorOnFirstLaunch(Installation installation) throws Exception {
        testServerError(installation, false);
    }

    @Test
    public void testServerErrorOnSecondLaunch(Installation installation) throws Exception {
        testServerError(installation, true);
    }

    public void testServerError(Installation installation, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.delete(installation.stage2Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testDownloadChecksumMismatchOnFirstLaunch(Installation installation) throws Exception {
        testDownloadChecksumMismatch(installation, false);
    }

    @Test
    public void testDownloadChecksumMismatchOnSecondLaunch(Installation installation) throws Exception {
        testDownloadChecksumMismatch(installation, true);
    }

    public void testDownloadChecksumMismatch(Installation installation, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage2Meta)), JsonObject.class);
        meta.addProperty("checksum", "00000000000000000000000000000000");
        Files.write(installation.stage2Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testDownloadServerErrorOnFirstLaunch(Installation installation) throws Exception {
        testDownloadServerError(installation, false);
    }

    @Test
    public void testDownloadServerErrorOnSecondLaunch(Installation installation) throws Exception {
        testDownloadServerError(installation, true);
    }

    public void testDownloadServerError(Installation installation, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage2Meta)), JsonObject.class);
        meta.addProperty("url", "https://127.0.0.1:9/invalid");
        meta.addProperty("checksum", "00000000000000000000000000000000"); // to get it to update on second launch
        Files.write(installation.stage2Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

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

    private IsolatedLaunch newDevLaunch(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.addToClasspath(installation.stage0JarFile.toUri().toURL());
        isolatedLaunch.addToClasspath(installation.mixin07JarFile.toUri().toURL());
        isolatedLaunch.addToClasspath(Paths.get("build", "classes", "java", "exampleMod").toUri().toURL());
        isolatedLaunch.addArg("--tweakClass", "gg.essential.loader.stage0.EssentialSetupTweaker");
        isolatedLaunch.setProperty("fml.coreMods.load", "com.example.mod.ExampleCoreMod");
        return isolatedLaunch;
    }

    @Test
    public void testInDev(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = newDevLaunch(installation);
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testEssentialTweakerModsInDev(Installation installation) throws Exception {
        installation.addExample2Mod("essential-tweaker");

        IsolatedLaunch isolatedLaunch = newDevLaunch(installation);
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("coreMod"), "Example2 CoreMod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mod"), "Example2 Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testInDevWithRelaunch(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = newDevLaunch(installation);
        isolatedLaunch.setProperty("essential.branch", "asm-52");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testEssentialTweakerModsInDevWithRelaunch(Installation installation) throws Exception {
        installation.addExample2Mod("essential-tweaker");

        IsolatedLaunch isolatedLaunch = newDevLaunch(installation);
        isolatedLaunch.setProperty("essential.branch", "asm-52");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("coreMod"), "Example2 CoreMod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mod"), "Example2 Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
