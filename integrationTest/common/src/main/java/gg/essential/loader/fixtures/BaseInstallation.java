package gg.essential.loader.fixtures;

import gg.essential.loader.util.Copy;
import gg.essential.loader.util.Delete;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class BaseInstallation implements AutoCloseable {
    private final Path originalApiDir = Paths.get("build", "downloadsApi");
    private final Path originalExampleModFile = originalApiDir.resolve("v1/mods/example/mod/updates/stable/" + getPlatformVersion() + ".jar");
    private final Path originalExample2ModFile = originalApiDir.resolve("v1/mods/example/mod2/updates/stable/" + getPlatformVersion() + ".jar");
    private final Path originalKotlinModFile = originalApiDir.resolve("v1/mods/example/kotlin/updates/stable/" + getPlatformVersion() + ".jar");

    public final Path gameDir = Files.createTempDirectory("game");
    public final Path modsDir = gameDir.resolve("mods");
    public final Path apiDir = gameDir.resolve("downloadsApi");
    public final Path mixin07JarFile = apiDir.resolve("v1/mods/essential/mixin/updates/07/" + getPlatformVersion() + ".jar");
    public final Path stage0JarFile = apiDir.resolve("v1/mods/essential/loader-stage0/updates/stable/" + getPlatformVersion() + ".jar");
    public final Path stage1Dummy = apiDir.resolve("v1/mods/essential/loader-stage1/updates/dummy/" + getPlatformVersion());
    public final Path stage2Meta = apiDir.resolve("v1/mods/essential/loader-stage2/updates/stable/" + getPlatformVersion());
    public final Path stage2DummyMeta = withBranch(stage2Meta, "dummy");
    public final Path stage3Meta = apiDir.resolve("v1/mods/essential/essential/updates/stable/" + getPlatformVersion());
    public final Path stage3DummyMeta = withBranch(stage3Meta, "dummy");

    public BaseInstallation() throws IOException {
        System.out.println("Installation: " + gameDir);

        Files.createDirectories(modsDir);
    }

    protected abstract String getPlatformVersion();

    public void setup() throws IOException {
        setupDownloadsApi();
    }

    protected void setupDownloadsApi() throws IOException {
        Copy.recursively(originalApiDir, apiDir);
    }

    public void addExampleMod() throws IOException {
        addExampleMod("stable");
    }

    public void addExampleMod(String branch) throws IOException {
        Files.copy(withBranch(originalExampleModFile, branch), modsDir.resolve("examplemod.jar"));
    }

    public void addExample2Mod() throws IOException {
        addExample2Mod("stable");
    }

    public void addExample2Mod(String branch) throws IOException {
        Files.copy(withBranch(originalExample2ModFile, branch), modsDir.resolve("example2mod.jar"));
    }

    public void addOldKotlinMod() throws IOException {
        // Alphabetically before everything else to be extra annoying
        Files.copy(withBranch(originalKotlinModFile, "old"), modsDir.resolve("_kotlin.jar"));
    }

    public IsolatedLaunch newLaunch(String tweaker) {
        IsolatedLaunch isolatedLaunch = new IsolatedLaunch(gameDir, tweaker);
        isolatedLaunch.setProperty("essential.download.url", apiDir.toUri().toString());
        return isolatedLaunch;
    }

    protected static IsolatedLaunch launch(IsolatedLaunch isolatedLaunch) throws Exception {
        isolatedLaunch.launch();
        return isolatedLaunch;
    }

    public IsolatedLaunch launch(String tweaker) throws Exception {
        return launch(newLaunch(tweaker));
    }

    public void assertModLaunched(IsolatedLaunch isolatedLaunch) throws Exception {
        assertTrue(isolatedLaunch.getModLoadState("tweaker"), "Example Tweaker ran");
        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
    }

    public void assertMod2Launched(IsolatedLaunch isolatedLaunch) throws Exception {
        assertTrue(isolatedLaunch.getMod2LoadState("tweaker"), "Example2 Tweaker ran");
        assertTrue(isolatedLaunch.getMod2LoadState("coreMod"), "Example2 CoreMod ran");
        assertTrue(isolatedLaunch.getMod2LoadState("mod"), "Example2 Mod ran");
    }

    @Override
    public void close() throws IOException {
        Delete.recursively(gameDir);
    }

    public static Path withBranch(Path endpoint, String branch) {
        return endpoint.getParent().resolveSibling(branch).resolve(endpoint.getFileName());
    }
}
