package gg.essential.loader.stage2.relaunch.args;

import net.minecraft.launchwrapper.Launch;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;

public class LaunchArgs {
    static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLTweaker";

    public static List<String> guessLaunchArgs() {
        String javaArgsStr = System.getProperty("sun.java.command");
        if (javaArgsStr == null) {
            return Fallback.guessLaunchArgs(); // property is not available on this JVM
        }
        List<String> javaArgs = splitIntoArguments(javaArgsStr);
        if (javaArgs.isEmpty()) {
            return Fallback.guessLaunchArgs(); // property is invalid
        }
        String main = javaArgs.remove(0);
        switch (main) {
            case "net.minecraft.launchwrapper.Launch":
                // Best case, we can just run the exact same thing again
                javaArgs.add(0, main);
                return javaArgs;
            case DevLaunchInjector.MAIN: // archloom
                // Almost best case, we just need to restore some system properties
                return DevLaunchInjector.getLaunchArgs(javaArgs);
            case "GradleStart": // ForgeGradle2
                // It's complicated
                return GradleStart.getLaunchArgs(javaArgs);
            case "org.multimc.EntryPoint":
            default:
                // Fallback is the best thing we got
                return Fallback.guessLaunchArgs();
        }
    }

    /**
     * Splits the given command line into arguments potentially containing space characters using heuristics such as
     * the launchArgs collected by Forge and whether an argument represents a valid, existing file path.
     */
    public static List<String> splitIntoArguments(String str) {
        @SuppressWarnings("unchecked")
        Map<String, String> launchArgs = (Map<String, String>) Launch.blackboard.get("launchArgs");

        Set<String> knownArgs = new HashSet<>();
        knownArgs.addAll(launchArgs.keySet());
        knownArgs.addAll(launchArgs.values());

        return splitIntoArguments(str, arg -> {
            if (knownArgs.contains(arg)) {
                return true;
            }
            try {
                if (Files.exists(Paths.get(arg))) {
                    return true;
                }
            } catch (InvalidPathException ignored) {
            }
            return false;
        });
    }

    /**
     * Splits the given command line into arguments potentially containing space characters by greedily including spaces
     * in the arguments if the given heuristic returns true for the combination.
     */
    private static List<String> splitIntoArguments(String str, Function<String, Boolean> isValidArg) {
        String[] parts = str.split(" ");
        List<String> args = new ArrayList<>();
        // Go from argument to argument
        for (int from = 0; from < parts.length; from++) {
            // And try to join as much as possible
            for (int to = parts.length; to > from; to--) {
                String joinedArg = join(parts, from, to);
                // but only accept it if it's a single argument (i.e. no space remaining) or the heuristic likes it
                if (from + 1 == to || isValidArg.apply(joinedArg)) {
                    args.add(joinedArg);
                    from = to - 1; // skip everything we just joined together
                    break;
                }
            }
        }
        return args;
    }

    private static String join(String[] array, int from, int to) {
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = from; i < to; i++) {
            joiner.add(array[i]);
        }
        return joiner.toString();
    }
}
