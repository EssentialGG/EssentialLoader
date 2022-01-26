package gg.essential.loader.stage2.relaunch.args;

import net.minecraft.launchwrapper.Launch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static gg.essential.loader.stage2.relaunch.args.LaunchArgs.FML_TWEAKER;

class Fallback {
    private static final String LITE_LOADER_TWEAKER = "com.mumfrey.liteloader.launch.LiteLoaderTweaker";

    static List<String> guessLaunchArgs() {
        // These are made available by FMLTweaker. This does unfortunately not include the keyword-less arguments but
        // I could find no way to access them and Vanilla doesn't use those anyway, so it should be fine.
        // They also do not contain duplicates (cause it's a map), nor do they contain tweakers.
        @SuppressWarnings("unchecked")
        Map<String, String> launchArgs = (Map<String, String>) Launch.blackboard.get("launchArgs");

        List<String> result = new ArrayList<>();
        result.add(Launch.class.getName());

        // Tweaker arguments are consumed by Launch.launch, I see no way to get them so we'll just assume it's always
        // FML, that should be the case for production in any ordinary setup.
        if (hasLiteLoader()) { // LiteLoader is not ordinary
            result.add("--tweakClass");
            result.add(LITE_LOADER_TWEAKER);
        }
        result.add("--tweakClass");
        result.add(FML_TWEAKER);

        for (Map.Entry<String, String> entry : launchArgs.entrySet()) {
            result.add(entry.getKey());
            result.add(entry.getValue());
        }

        return result;
    }

    private static boolean hasLiteLoader() {
        try {
            return Launch.classLoader.getClassBytes(LITE_LOADER_TWEAKER) != null;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
