package gg.essential.loader.fixtures;

import com.sun.net.httpserver.HttpServer;
import gg.essential.loader.util.Copy;
import gg.essential.loader.util.Delete;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class BaseInstallation implements AutoCloseable {
    private final Path originalApiDir = Paths.get("build", "downloadsApi");
    private final Path originalExampleModFile = originalApiDir.resolve("v1/example:mod/versions/stable/platforms/" + getPlatformVersion() + ".jar");
    private final Path originalExample2ModFile = originalApiDir.resolve("v1/example:mod2/versions/stable/platforms/" + getPlatformVersion() + ".jar");
    private final Path originalKotlinModFile = originalApiDir.resolve("v1/example:kotlin/versions/stable/platforms/" + getPlatformVersion() + ".jar");

    public final Path gameDir = Files.createTempDirectory("test game dir"); // spaces to make sure it can handle those
    public final Path modsDir = gameDir.resolve("mods");
    public final Path apiDir = gameDir.resolve("downloadsApi");
    public final Path essentialDir = gameDir.resolve("essential");
    public final Path mixin07JarFile = apiDir.resolve("v1/essential:mixin/versions/07/platforms/" + getPlatformVersion() + ".jar");
    public final Path stage0JarFile = apiDir.resolve("v1/essential:loader-stage0/versions/stable/platforms/" + getPlatformVersion() + ".jar");
    public final Path stage1Dummy = apiDir.resolve("v1/essential:loader-stage1/versions/dummy/platforms/" + getPlatformVersion() + ".json");
    public final Path stage2Meta = apiDir.resolve("v1/essential:loader-stage2/versions/stable/platforms/" + getPlatformVersion() + ".json");
    public final Path stage2DummyMeta = withBranch(stage2Meta, "dummy");
    public final Path stage3Meta = apiDir.resolve("v1/essential:essential/versions/stable/platforms/" + getPlatformVersion() + ".json");
    public final Path stage3MetaDownload = stage3Meta.resolveSibling(getPlatformVersion()).resolve("download.json");
    public final Path stage3JarFile = apiDir.resolve("v1/essential:essential/versions/stable/platforms/" + getPlatformVersion() + ".jar");
    public final Path stage3DummyMeta = withBranch(stage3Meta, "dummy");
    public final Path stage3DummyMetaDownload = stage3DummyMeta.resolveSibling(getPlatformVersion()).resolve("download.json");
    public final Path stage3DummyMetaDiff = apiDir.resolve("v1/essential:essential/versions/stable/diff/dummy/platforms/" + getPlatformVersion() + ".json");
    public final Path stage3DummyJarFile = apiDir.resolve("v1/essential:essential/versions/dummy/platforms/" + getPlatformVersion() + ".jar");

    private final HttpServer server;
    private final String downloadApiUrl;

    public BaseInstallation() throws IOException {
        System.out.println("Installation: " + gameDir);

        Files.createDirectories(modsDir);

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", httpExchange -> {
            Path path = apiDir.resolve(httpExchange.getRequestURI().getPath().substring(1));
            if (!Files.isRegularFile(path)) {
                path = path.resolveSibling(path.getFileName().toString() + ".json");
            }
            if (Files.exists(path)) {
                byte[] bytes = Files.readAllBytes(path);
                httpExchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = httpExchange.getResponseBody()) {
                    out.write(bytes);
                }
            } else {
                httpExchange.sendResponseHeaders(404, 0);
                httpExchange.getResponseBody().close();
            }
        });
        server.setExecutor(null);
        server.start();
        InetSocketAddress address = server.getAddress();
        downloadApiUrl = "http://" + address.getHostString() + ":" + address.getPort();
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
        Files.copy(withBranch(originalExampleModFile, branch), modsDir.resolve("examplemod.jar"), StandardCopyOption.REPLACE_EXISTING);
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
        isolatedLaunch.setProperty("essential.download.url", downloadApiUrl);
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
        server.stop(0);
        Delete.recursively(gameDir);
    }

    public static Path withBranch(Path endpoint, String branch) {
        return endpoint.getParent().getParent().resolveSibling(branch).resolve("platforms").resolve(endpoint.getFileName());
    }
}
