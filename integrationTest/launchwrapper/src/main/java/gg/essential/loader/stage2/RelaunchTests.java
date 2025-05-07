package gg.essential.loader.stage2;

import gg.essential.loader.fixtures.Installation;
import gg.essential.loader.fixtures.IsolatedLaunch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelaunchTests {
    private static final String SUN_JAVA_COMMAND = "sun.java.command";
    private static final String LAUNCH_WRAPPER_MAIN = "net.minecraft.launchwrapper.Launch";
    private static final String DEV_LAUNCH_INJECTOR_MAIN = "net.fabricmc.devlaunchinjector.Main";
    private static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLTweaker";

    @Test
    public void testRelaunchOfInitPhaseMixinWithMixin07(Installation installation) throws Exception {
        testRelaunchOfInitPhaseMixin(installation, "07");
    }

    @Test
    public void testRelaunchOfInitPhaseMixinWithMixin08(Installation installation) throws Exception {
        testRelaunchOfInitPhaseMixin(installation, "08");
    }

    public void testRelaunchOfInitPhaseMixin(Installation installation, String outerMixinVersion) throws Exception {
        installation.addExampleMod("stable-with-mixin-" + outerMixinVersion);
        installation.addExample2Mod("mixin-tweaker-with-mixin-" + outerMixinVersion);

        // The issue we test here only happens on 1.12.2 because older Log4J versions simply overwrite existing
        // appenders, so the inner Mixin's appender get registered as expected. Newer versions however do not modify
        // the appenders if one with the same name already exists, thereby breaking the inner mixin if we do not fix it.
        IsolatedLaunch isolatedLaunch = installation.newLaunchFML11202();
        isolatedLaunch.setProperty("essential.branch", "mixin-08");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.getModLoadState("mixinInitPhase"), "Example INIT-phase mixin applied");
        assertTrue(isolatedLaunch.getModLoadState("mixin"), "Example mixin plugin ran");
        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
    }

    private IsolatedLaunch newDevLaunch(Installation installation, String...javaArgs) throws IOException {
        IsolatedLaunch launch = installation.newLaunchFML();
        configureDevLaunch(launch, installation, javaArgs);
        return launch;
    }

    private void configureDevLaunch(IsolatedLaunch launch, Installation installation, String...javaArgs) throws IOException {
        Path folderWithSpace = installation.gameDir.resolve("test folder");
        Files.createDirectory(folderWithSpace);

        List<String> args = Arrays.asList(
            "--tweakClass", "com.example.mod.tweaker.ExampleModTweaker",
            "--password", "123",
            "--tweakClass=com.example.mod.tweaker.ExampleModSecondTweaker",
            "--accessToken=aoeu",
            "standalone",
            // These need to go last cause FMLTweaker messes up everything after a duplicate argument (it doesn't get to
            // see tweakers, Launch consumes those, hence why above --tweakClass is fine)
            "--dummy",
            // This value is an existing folder with spaces in its name. It's the first of a duplicate keyword argument
            // so fml won't keep it, and we can't rely on that heuristic, so we actually have to check the file system
            // to recover this value.
            folderWithSpace.toString(),
            "--dummy",
            // This value is just an arbitrary argument with spaces, fml will keep it and we should be able to infer
            // that it's a single argument based on that.
            "2 3"
        );

        // Add all the test arguments to the launch configuration
        for (String arg : args) {
            if (javaArgs[0].equals("GradleStart")) {
                // A real GradleStart eats those (but we aren't running the real thing, so we eat them manually)
                if (arg.equals("--password") || arg.equals("123")) continue;
            }
            launch.addArg(arg);
        }

        // Immediately add stage0 and the example mod to the classpath like in dev so we can test custom tweakers
        launch.addToClasspath(installation.stage0JarFile.toUri().toURL());
        launch.addToClasspath(Paths.get("build", "classes", "java", "exampleMod").toUri().toURL());
        // Instruct forge to load examplemod's coremod (dunno why this is done via a property rather than an argument)
        launch.setProperty("fml.coreMods.load", "com.example.mod.ExampleCoreMod");

        // Set the java command property to emulate us launching a JVM
        String command = String.join(" ", javaArgs) // with the passed java main and arguments
            + " " + String.join(" ", args) // as well as our test arguments
            + " --gameDir " + launch.getGameDir().toString() // and this argument implicitly added by IsolatedLaunch
            ;
        launch.setProperty(SUN_JAVA_COMMAND, command);
    }

    @Test
    public void testRelaunchArgs_UnknownMain(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = newDevLaunch(installation, "some.unknown.launcher.Main");
        isolatedLaunch.launch();

        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getModLoadState("coreMod"), "Example CoreMod ran");
        assertTrue(isolatedLaunch.getModLoadState("mod"), "Example Mod ran");
        assertTrue(isolatedLaunch.getModLoadState("relaunched"), "Re-launched");

        // For unknown main methods, we cannot preserve the tweakers
        assertFalse(isolatedLaunch.getModLoadState("tweaker"), "Example Tweaker ran");
        assertFalse(isolatedLaunch.getModLoadState("secondTweaker"), "Example Mod Secondary Tweaker ran");
        // and only get unique keyword arguments
        assertEffectiveArgs(isolatedLaunch, false,
            "--password", "123", "--dummy", "2 3");
    }

    @Test
    public void testRelaunchArgs_LaunchWrapper(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = newDevLaunch(installation, LAUNCH_WRAPPER_MAIN, "--tweakClass", FML_TWEAKER);
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getModLoadState("relaunched"), "Re-launched");

        // For LaunchWrapper we should be able to fully recover everything
        assertTrue(isolatedLaunch.getModLoadState("secondTweaker"), "Example Mod Secondary Tweaker ran");
        assertEffectiveArgs(isolatedLaunch, true,
            "--password", "123", "standalone", "--dummy", "GAME_DIR/test folder", "--dummy", "2 3");
    }

    @Test
    public void testRelaunchArgs_GradleStart(Installation installation) throws Exception {
        IsolatedLaunch isolatedLaunch = installation.newLaunchFML();
        isolatedLaunch.addArg("--uuid", "UUID");
        configureDevLaunch(isolatedLaunch, installation, "GradleStart");
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getModLoadState("relaunched"), "Re-launched");

        // For GradleStart we should be able to effectively recover everything but not cleanly
        // password is consumed by GradleStart, uuid was produced by it (in production it would be; for the test
        // we added it to the launch args but not the property)
        assertTrue(isolatedLaunch.getModLoadState("secondTweaker"), "Example Mod Secondary Tweaker ran");
        assertEffectiveArgs(isolatedLaunch, true,
            "--uuid", "UUID", "standalone", "--dummy", "GAME_DIR/test folder", "--dummy", "2 3");
    }

    @Test
    public void testRelaunchArgs_DevLaunchInjector(Installation installation) throws Exception {
        String dliConfig = "clientArgs\n\t--tweakClass\n\t" + FML_TWEAKER;

        Path dliConfigPath = installation.gameDir.resolve("dli-config.toml");
        Files.write(dliConfigPath, dliConfig.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);

        IsolatedLaunch isolatedLaunch = newDevLaunch(installation, DEV_LAUNCH_INJECTOR_MAIN);
        isolatedLaunch.setProperty("test.boot-prefix", "test."); // because we cannot actually set the boot properties
        isolatedLaunch.setProperty("test.fabric.dli.main", LAUNCH_WRAPPER_MAIN);
        isolatedLaunch.setProperty("test.fabric.dli.env", "client");
        isolatedLaunch.setProperty("test.fabric.dli.config", dliConfigPath.toAbsolutePath().toString());
        isolatedLaunch.launch();

        installation.assertModLaunched(isolatedLaunch);
        assertTrue(isolatedLaunch.isEssentialLoaded(), "Essential loaded");
        assertTrue(isolatedLaunch.getModLoadState("relaunched"), "Re-launched");

        // For DLI we should be able to fully recover everything
        assertTrue(isolatedLaunch.getModLoadState("secondTweaker"), "Example Mod Secondary Tweaker ran");
        assertEffectiveArgs(isolatedLaunch, true,
            "--password", "123", "standalone", "--dummy", "GAME_DIR/test folder", "--dummy", "2 3");
    }

    private void assertEffectiveArgs(IsolatedLaunch launch, boolean tweaker, String... expected) throws Exception {
        String[] actual = (String[]) launch
            // We get more accurate results from the tweaker (cause Forge doesn't yet squash duplicate arguments)
            // but that only works when the tweaker is actually preserved during relaunch (it is not for unknown mains).
            .getClass(tweaker ? "sun.com.example.mod.LoadState" : "sun.minecraft.LoadState")
            .getDeclaredField("args")
            .get(null);

        assertNotNull(actual, (tweaker ? "Example Mod Tweaker" : "Minecraft") + " launch arguments");

        String gameDir = launch.getGameDir().toString();

        // Remove arguments which we do not really care about
        int to = 0;
        for (int i = 0; i < actual.length; i++) {
            String arg = actual[i];

            // this gets added by the DelayedStage0Tweaker, it's an old, narrow workaround for the relaunch issue
            if ("--tweakClass".equals(arg) && "gg.essential.loader.stage0.EssentialSetupTweaker".equals(actual[i + 1])) {
                i++; // skip value
                continue;
            }

            // Strip the non-deterministic installation folder name
            if (arg.contains(gameDir)) {
                arg = arg.replace(gameDir, "GAME_DIR");
            }

            // forge adds a dummy value for this, we don't really care
            if ("--version".equals(arg)) {
                i++; // skip value
                continue;
            }

            actual[to++] = arg; // keep it
        }
        actual = Arrays.copyOf(actual, to);

        Map<String, List<String>> expectedMap = parseArguments(expected);
        Map<String, List<String>> actualMap = parseArguments(actual);

        // these should always be present
        expectedMap.put("--gameDir", Collections.singletonList("GAME_DIR"));
        expectedMap.put("--accessToken", Collections.singletonList("aoeu"));

        assertEquals(expectedMap, actualMap);
    }

    /**
     * Parses the given arguments into a key-value map. Key-less arguments are added to the empty string key.
     * The resulting map is sorted because FMLTweaker effectively shuffles all arguments by putting them into a HashMap.
     *
     * This does not handle keyed arguments without values because we do not use those.
     */
    private Map<String, List<String>> parseArguments(String[] args) {
        Map<String, List<String>> result = new TreeMap<>();
        String key = "";
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.contains("=")) {
                    String[] split = arg.split("=");
                    result.computeIfAbsent(split[0], __ -> new ArrayList<>()).add(split[1]);
                } else {
                    key = arg;
                }
            } else {
                result.computeIfAbsent(key, __ -> new ArrayList<>()).add(arg);
                key = "";
            }
        }
        return result;
    }
}
