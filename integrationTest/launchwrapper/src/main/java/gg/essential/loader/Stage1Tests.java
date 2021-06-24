package gg.essential.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1Tests {
    @Test
    public void testUpdate() throws Exception {
        Installation installation = new Installation();
        installation.setup();
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
    public void testUnsupportedVersion() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.write(installation.stage2Meta, new byte[0]);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testJsonSyntaxInvalid() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.write(installation.stage2Meta, "{ oh no }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testJsonContentInvalid() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.write(installation.stage2Meta, "{ \"url\": 42 }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testServerError() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.delete(installation.stage2Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testDownloadChecksumMismatch() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage2Meta)), JsonObject.class);
        meta.addProperty("checksum", "00000000000000000000000000000000");
        Files.write(installation.stage2Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testDownloadServerError() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage2Meta)), JsonObject.class);
        meta.addProperty("url", "https://127.0.0.1:9/invalid");
        Files.write(installation.stage2Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testMultipleCustomTweakerMods() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();
        installation.addExample2Mod();

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        installation.assertMod2Launched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testMultipleEssentialTweakerMods() throws Exception {
        Installation installation = new Installation();
        installation.setup();
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
    public void testMultipleCustomTweakerModsWithMixin07() throws Exception {
        testMultipleCustomTweakerModsWithMixin("07");
    }

    @Test
    public void testMultipleCustomTweakerModsWithMixin08() throws Exception {
        testMultipleCustomTweakerModsWithMixin("08");
    }

    public void testMultipleCustomTweakerModsWithMixin(String mixinVersion) throws Exception {
        Installation installation = new Installation();
        installation.setup();
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
    public void testMultipleEssentialTweakerModsWithMixin07() throws Exception {
        testMultipleEssentialTweakerModsWithMixin("07");
    }

    @Test
    public void testMultipleEssentialTweakerModsWithMixin08() throws Exception {
        testMultipleEssentialTweakerModsWithMixin("08");
    }

    public void testMultipleEssentialTweakerModsWithMixin(String mixinVersion) throws Exception {
        Installation installation = new Installation();
        installation.setup();
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
}
