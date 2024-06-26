package gg.essential.loader.fixtures;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Installation extends BaseInstallation {

    public final Path stage1Folder = essentialDir.resolve("loader").resolve("stage1").resolve("launchwrapper");
    public final Path stage1ConfigFile = stage1Folder.resolve("stage2.forge_1.8.8.properties");
    public final Path stage2ConfigFile = essentialDir.resolve("essential-loader.properties");

    public Installation() throws IOException {
        Path dummyFile = Files.createTempFile("dummy", "");
        System.setProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", dummyFile.toAbsolutePath().toString());
    }

    @Override
    protected String getPlatformVersion() {
        return "forge_1-8-8";
    }

    public IsolatedLaunch newLaunchFMLWithoutRuntime() {
        return newLaunch("net.minecraftforge.fml.common.launcher.FMLTweaker");
    }

    public IsolatedLaunch newLaunchFML(String version) throws MalformedURLException {
        IsolatedLaunch launch = newLaunchFMLWithoutRuntime();
        launch.addToClasspath(apiDir.resolve("v1/forge:runtime/versions/stable/platforms/" + version + ".jar").toUri().toURL());
        return launch;
    }

    public IsolatedLaunch newLaunchFML10808() throws MalformedURLException {
        return newLaunchFML("forge_1-8-8");
    }

    public IsolatedLaunch newLaunchFML11202() throws MalformedURLException {
        IsolatedLaunch launch = newLaunchFML("forge_1-12-2");
        launch.setProperty("log4j.configurationFile", "log4j2-test.xml");
        return launch;
    }

    public IsolatedLaunch newLaunchFML() throws MalformedURLException {
        return newLaunchFML10808();
    }

    public IsolatedLaunch launchFML() throws Exception {
        return launch(newLaunchFML());
    }
}
