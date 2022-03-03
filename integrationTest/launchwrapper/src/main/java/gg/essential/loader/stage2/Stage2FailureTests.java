package gg.essential.loader.stage2;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Stage2FailureTests {
    @Test
    public void testVersionJsonSyntaxInvalidFirstLaunch(Installation installation) throws Exception {
        testJsonSyntaxInvalid(installation, it -> it.stage3Meta, false);
    }

    @Test
    public void testVersionJsonSyntaxInvalidOnSecondLaunch(Installation installation) throws Exception {
        testJsonSyntaxInvalid(installation, it -> it.stage3Meta, true);
    }

    @Test
    public void testDownloadJsonSyntaxInvalidFirstLaunch(Installation installation) throws Exception {
        testJsonSyntaxInvalid(installation, it -> it.stage3MetaDownload, false);
    }

    @Test
    public void testDownloadJsonSyntaxInvalidOnSecondLaunch(Installation installation) throws Exception {
        testJsonSyntaxInvalid(installation, it -> it.stage3MetaDownload, true);
    }

    public void testJsonSyntaxInvalid(Installation installation, Function<Installation, Path> file, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.write(file.apply(installation), "{ oh no }".getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    @Test
    public void testVersionJsonContentInvalidFirstLaunch(Installation installation) throws Exception {
        testJsonContentInvalid(installation, it -> it.stage3Meta, false);
    }

    @Test
    public void testVersionJsonContentInvalidOnSecondLaunch(Installation installation) throws Exception {
        testJsonContentInvalid(installation, it -> it.stage3Meta, true);
    }

    @Test
    public void testDownloadJsonContentInvalidFirstLaunch(Installation installation) throws Exception {
        testJsonContentInvalid(installation, it -> it.stage3MetaDownload, false);
    }

    @Test
    public void testDownloadJsonContentInvalidOnSecondLaunch(Installation installation) throws Exception {
        testJsonContentInvalid(installation, it -> it.stage3MetaDownload, true);
    }

    public void testJsonContentInvalid(Installation installation, Function<Installation, Path> file, boolean secondLaunch) throws Exception {
        installation.addExampleMod();

        if (secondLaunch) {
            installation.launchFML();
        }

        Files.write(file.apply(installation), "{ \"url\": 42 }".getBytes(StandardCharsets.UTF_8));

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

        Files.delete(installation.stage3Meta);

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
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage3MetaDownload)), JsonObject.class);
        meta.addProperty("checksum", "00000000000000000000000000000000");
        Files.write(installation.stage3MetaDownload, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

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
        JsonObject meta = gson.fromJson(new String(Files.readAllBytes(installation.stage3MetaDownload)), JsonObject.class);
        meta.addProperty("url", "https://127.0.0.1:9/invalid");
        meta.addProperty("checksum", "00000000000000000000000000000000"); // to get it to update on second launch
        Files.write(installation.stage3MetaDownload, gson.toJson(meta).getBytes(StandardCharsets.UTF_8));

        IsolatedLaunch isolatedLaunch = installation.launchFML();

        installation.assertModLaunched(isolatedLaunch);
        assertEquals(secondLaunch, isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }
}
