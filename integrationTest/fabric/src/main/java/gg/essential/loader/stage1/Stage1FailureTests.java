package gg.essential.loader.stage1;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage1FailureTests {
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
            installation.launchFabric();
        }

        Files.write(installation.stage2Meta, "{ oh no }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

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
            installation.launchFabric();
        }

        Files.write(installation.stage2Meta, "{ \"url\": 42 }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

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
            installation.launchFabric();
        }

        Files.delete(installation.stage2Meta);

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

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
            installation.launchFabric();
        }

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage2Meta)), JsonObject.class);
        meta.addProperty("checksum", "00000000000000000000000000000000");
        Files.write(installation.stage2Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

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
            installation.launchFabric();
        }

        Gson gson = new Gson();
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage2Meta)), JsonObject.class);
        meta.addProperty("url", "https://127.0.0.1:9/invalid");
        meta.addProperty("checksum", "00000000000000000000000000000000"); // to get it to update on second launch
        Files.write(installation.stage2Meta, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFabric();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
