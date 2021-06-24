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
    public void testUnsupportedVersion() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.write(installation.stage3Meta, new byte[0]);

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testJsonSyntaxInvalid() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.write(installation.stage3Meta, "{ oh no }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testJsonContentInvalid() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.write(installation.stage3Meta, "{ \"url\": 42 }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testServerError() throws Exception {
        Installation installation = new Installation();
        installation.setup();
        installation.addExampleMod();

        Files.delete(installation.stage3Meta);

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
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage3Meta)), JsonObject.class);
        meta.addProperty("checksum", "00000000000000000000000000000000");
        Files.write(installation.stage3Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

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
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage3Meta)), JsonObject.class);
        meta.addProperty("url", "https://127.0.0.1:9/invalid");
        Files.write(installation.stage3Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertFalse(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
