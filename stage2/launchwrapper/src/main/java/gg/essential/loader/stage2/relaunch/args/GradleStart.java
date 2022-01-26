package gg.essential.loader.stage2.relaunch.args;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.minecraft.launchwrapper.Launch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static gg.essential.loader.stage2.relaunch.args.LaunchArgs.FML_TWEAKER;

class GradleStart {
    static List<String> getLaunchArgs(List<String> javaArgs) {
        // Some options are handled by GradleStart and we want to use whatever it comes up with instead of the ones
        // originally passed in.
        javaArgs = removeOptionsHandledByGradleStart(javaArgs);

        // We'll build our arguments based on:
        List<String> result = new ArrayList<>();

        // - Targeting Launch.main directly because GradleStart is horribly impure so we can't just call it again
        result.add(Launch.class.getName());

        // - an educated guess for tweakers added by GradleStart
        result.add("--tweakClass");
        result.add(FML_TWEAKER);

        // - the arguments synthesized by GradleStart
        for (Map.Entry<String, String> syntheticArg : getSyntheticArguments(javaArgs).entrySet()) {
            result.add(syntheticArg.getKey());
            result.add(syntheticArg.getValue());
        }

        // - all remaining arguments from the java property
        result.addAll(javaArgs);

        return result;
    }

    private static List<String> removeOptionsHandledByGradleStart(List<String> javaArgs) {
        // We need to parse the arguments to remove those which GradleStart has processed from the java args and to
        // extract those which it has generated from the launchArgs.
        List<String> keysHandledByGradleStart = Arrays.asList(
            // See GradleStart.setDefaultArguments for a list of those which it handles
            "version", "assetIndex", "assetsDir", "accessToken", "userProperties", "username", "password");

        OptionParser parser = new OptionParser();
        for (String key : keysHandledByGradleStart) {
            parser.accepts(key).withRequiredArg().ofType(String.class);
        }
        parser.allowsUnrecognizedOptions();
        NonOptionArgumentSpec<String> nonOption = parser.nonOptions();

        OptionSet options = parser.parse(javaArgs.toArray(new String[0]));

        return nonOption.values(options);
    }

    /**
     * Returns arguments synthesized by GradleStart, i.e. not present in the original java arguments.
     */
    private static Map<String, String> getSyntheticArguments(List<String> originalArguments) {
        // These are made available by FMLTweaker. It doesn't include keyword-less arguments, nor does it contain
        // duplicates but it does contain some arguments computed by GradleStart which we want to get our hands on.
        @SuppressWarnings("unchecked")
        Map<String, String> launchArgs = (Map<String, String>) Launch.blackboard.get("launchArgs");

        // These are the keyword arguments that are actually in the original java arguments
        Map<String, String> originalArgs = parseKeywordArgsLikeFMLTweaker(originalArguments);
        // Our DelayedStage0Tweaker manually adds a --tweakClass for itself in case of relaunch,
        // we do not need it in this case though cause we can recover the original arguments, including tweakers.
        originalArgs.put("--tweakClass", "");

        // We want to return those launch arguments that aren't present in the original java arguments
        return launchArgs.entrySet().stream()
            .filter(it -> !originalArgs.containsKey(it.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Parses the list of arguments into keyword arguments exactly the same way as FMLTweaker would.
     */
    private static Map<String, String> parseKeywordArgsLikeFMLTweaker(List<String> args) {
        // First we must remove arguments handled by Launch itself because those may appear more than once and
        // forge doesn't properly handle that. Or they may appear after unrelated duplicate arguments, and forge doesn't
        // properly handle those either.
        OptionParser parser = new OptionParser();
        parser.accepts("tweakClass").withRequiredArg().ofType(String.class);
        ArgumentAcceptingOptionSpec<String> gameDir = parser.accepts("gameDir").withRequiredArg().ofType(String.class);
        parser.allowsUnrecognizedOptions();
        NonOptionArgumentSpec<String> nonOption = parser.nonOptions();

        OptionSet options = parser.parse(args.toArray(new String[0]));
        args = nonOption.values(options);

        // Then we can parse the remained in the exact same stupid way forge does
        Map<String, String> result = new LinkedHashMap<>();
        String classifier = null;
        for (String arg : args) {
            // The way Forge reassigns classifier in some of these cases is really dumb and incorrect, but we gotta go
            // with it to get accurate results
            if (arg.startsWith("-")) {
                if (classifier != null) {
                    classifier = result.put(classifier, "");
                } else if (arg.contains("=")) {
                    int idx = arg.indexOf('=');
                    classifier = result.put(arg.substring(0, idx), arg.substring(idx + 1));
                } else {
                    classifier = arg;
                }
            } else if (classifier != null) {
                classifier = result.put(classifier, arg);
            }
        }
        // Forge manually re-adds the gameDir argument
        result.put("--gameDir", gameDir.value(options));
        // Forge manually adds the version argument if it is missing
        result.putIfAbsent("--version", "");
        return result;
    }
}
