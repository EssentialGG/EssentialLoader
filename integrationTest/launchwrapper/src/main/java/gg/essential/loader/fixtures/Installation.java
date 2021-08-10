package gg.essential.loader.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Installation extends BaseInstallation {

    public Installation() throws IOException {
        Path dummyFile = Files.createTempFile("dummy", "");
        System.setProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", dummyFile.toAbsolutePath().toString());
    }

    @Override
    protected String getPlatformVersion() {
        return "forge_1-8-8";
    }

    public IsolatedLaunch launchFML() throws Exception {
        return launch("net.minecraftforge.fml.common.launcher.FMLTweaker");
    }
}
