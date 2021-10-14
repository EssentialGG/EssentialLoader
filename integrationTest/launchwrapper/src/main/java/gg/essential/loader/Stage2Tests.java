package gg.essential.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage2Tests {
    @Test
    public void testUpdate() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        installation.launchFML();

        Files.delete(installation.stage3Meta);
        Files.copy(installation.stage3DummyMeta, installation.stage3Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.getClass("gg.essential.api.tweaker.EssentialTweaker").getDeclaredField("dummyInitialized").getBoolean(null));
    }

    @Test
    public void testUnsupportedVersionOnFirstLaunch() throws Exception {
        testUnsupportedVersion(false);
    }

    @Test
    public void testUnsupportedVersionOnSecondLaunch() throws Exception {
        testUnsupportedVersion(true);
    }

    public void testUnsupportedVersion(boolean secondLaunch) throws Exception {
        Installation installation = new Installation();
        installation.setup();
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
    public void testJsonSyntaxInvalidFirstLaunch() throws Exception {
        testJsonSyntaxInvalid(false);
    }

    @Test
    public void testJsonSyntaxInvalidOnSecondLaunch() throws Exception {
        testJsonSyntaxInvalid(true);
    }

    public void testJsonSyntaxInvalid(boolean secondLaunch) throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.write(installation.stage3Meta, "{ oh no }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testJsonContentInvalidOnFirstLaunch() throws Exception {
        testJsonContentInvalid(false);
    }

    @Test
    public void testJsonContentInvalidOnSecondLaunch() throws Exception {
        testJsonContentInvalid(true);
    }

    public void testJsonContentInvalid(boolean secondLaunch) throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.write(installation.stage3Meta, "{ \"url\": 42 }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testServerErrorOnFirstLaunch() throws Exception {
        testServerError(false);
    }

    @Test
    public void testServerErrorOnSecondLaunch() throws Exception {
        testServerError(true);
    }

    public void testServerError(boolean secondLaunch) throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.delete(installation.stage3Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testDownloadChecksumMismatchOnFirstLaunch() throws Exception {
        testDownloadChecksumMismatch(false);
    }

    @Test
    public void testDownloadChecksumMismatchOnSecondLaunch() throws Exception {
        testDownloadChecksumMismatch(true);
    }

    public void testDownloadChecksumMismatch(boolean secondLaunch) throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage3Meta)), JsonObject.class);
        meta.addProperty("checksum", "00000000000000000000000000000000");
        Files.write(installation.stage3Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testDownloadServerErrorOnFirstLaunch() throws Exception {
        testDownloadServerError(false);
    }

    @Test
    public void testDownloadServerErrorOnSecondLaunch() throws Exception {
        testDownloadServerError(true);
    }

    public void testDownloadServerError(boolean secondLaunch) throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage3Meta)), JsonObject.class);
        meta.addProperty("url", "https://127.0.0.1:9/invalid");
        meta.addProperty("checksum", "00000000000000000000000000000000"); // to get it to update on second launch
        Files.write(installation.stage3Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testOldKotlinOnClasspath() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();
        installation.addOldKotlinMod();

        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.setProperty("essential.loader.relaunch", "false"); // do not want to re-launch for this one
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testRelaunchDueToOldKotlin() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();
        installation.addOldKotlinMod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testRelaunchDueToOldMixin() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod("stable-with-mixin-07");

        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.setProperty("essential.branch", "mixin-08");
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testRelaunchDueToKotlinBeingExcluded() throws Exception {
        Installation installation = new Installation();
        installation.setup();
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
