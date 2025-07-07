package gg.essential.loader.fixtures;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;

public class Installation extends BaseInstallation {

    static {
        // Initialize this as early as possible so it actually takes effect inside the isolated launch.
        // See https://github.com/google/jimfs/commit/f2503678be1a49023c27a023058f8202b9deea74
        try {
            Class.forName("com.google.common.jimfs.JimfsFileSystemProvider");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public final Path stage1Folder = essentialDir.resolve("loader").resolve("stage1").resolve("fabric");
    public final Path stage1ConfigFile = stage1Folder.resolve("stage2.fabric_1.14.4.properties");
    public final Path stage2ConfigFile = essentialDir.resolve("essential-loader.properties");

    public Installation() throws IOException {
        // Current fabric-loader breaks on LegacyLauncher if there is a JiJ mod and MC needs to be decompiled, so we
        // launch once without any mods to get the decompiled MC and can the proceed as usual.
        PrintStream orgOut = System.out;
        System.setOut(new PrintStream(ByteStreams.nullOutputStream()));
        try {
            launchFabric();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(orgOut);
        }
    }

    @Override
    protected String getPlatformVersion() {
        return "fabric_1-14-4";
    }

    public IsolatedLaunch newLaunchFabric() throws Exception {
        // Clean up old JimFS instances which may linger around (stored in a weak map) from previous launches
        try {
            FileSystems.getFileSystem(new URI("jimfs://nestedJarStore")).close();
        } catch (ProviderNotFoundException | FileSystemNotFoundException ignored) {
        }

        return this.newLaunch("net.fabricmc.loader.launch.FabricClientTweaker");
    }

    public IsolatedLaunch launchFabric() throws Exception {
        return launch(newLaunchFabric());
    }

    public void addJijMod(String branch) throws IOException {
        Path source = apiDir.resolve("v1/example:jij/versions/" + branch + "/platforms/" + getPlatformVersion() + ".jar");
        Files.copy(source, modsDir.resolve("jij-" + branch + ".jar"));
    }
}
