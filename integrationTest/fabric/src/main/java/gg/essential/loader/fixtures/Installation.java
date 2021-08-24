package gg.essential.loader.fixtures;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;

public class Installation extends BaseInstallation {

    public Installation() throws IOException {
        // Current fabric-loader breaks on LegacyLauncher if there is a JiJ mod and MC needs to be decompiled, so we
        // launch once without any mods to get the decompiled MC and can the proceed as usual.
        try {
            launchFabric();
        } catch (Exception e) {
            throw new RuntimeException(e);
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
}
