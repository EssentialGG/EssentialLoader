//
// This file is a copy of the one in the :stage1:launchwrapper project. Keep in sync.
//
package gg.essential.loader.stage1;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.FMLRelaunchLog;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * When launched in a dev environment via a `--tweakClass` argument, we will load in an earlier cycle than in production
 * (where only the FMLTweaker loads in the first cycle, and it then queues us for the second one). This means that if
 * there are any Essential in the mods folder, that those are not yet on the classpath at this time, which in turn means
 * that our platform setup code does not consider them, meaning they won't load as mods.
 * To work around that (and get dev closer to production), if we detect that we are in the first cycle, we will instead
 * queue this tweaker for the second one (as a fake stage0) and then proceed as usual from there.
 */
public class DelayedStage0Tweaker implements ITweaker {
    private static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLTweaker";
    private static final String COMMAND_LINE_COREMODS_PROP = "fml.coreMods.load";

    private static ITweaker realStage0;
    private static String[] commandLineCoremods; // we also delay these cause they may depend on our stuff
    private final ITweaker stage1;

    @SuppressWarnings("unused")
    public DelayedStage0Tweaker() throws Exception {
        if (commandLineCoremods.length > 0) {
            // Temporarily restore these in case we need to re-launch (they do not need to be delayed in that case)
            System.setProperty(COMMAND_LINE_COREMODS_PROP, String.join(",", commandLineCoremods));
        }

        this.stage1 = new EssentialSetupTweaker(realStage0);

        System.clearProperty(COMMAND_LINE_COREMODS_PROP);

        for (String commandLineCoremod : commandLineCoremods) {
            FMLRelaunchLog.info("Found a command line coremod : %s", commandLineCoremod);

            Method loadCoreMod = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
            loadCoreMod.setAccessible(true);
            ITweaker tweaker = (ITweaker) loadCoreMod.invoke(null, Launch.classLoader, commandLineCoremod, null);

            if (tweaker != null) {
                @SuppressWarnings("unchecked")
                List<ITweaker> tweakers = ((List<ITweaker>) Launch.blackboard.get("Tweaks"));
                tweakers.add(tweaker);
            }
        }
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.stage1.acceptOptions(args, gameDir, assetsDir, profile);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        this.stage1.injectIntoClassLoader(classLoader);
    }

    @Override
    public String getLaunchTarget() {
        return this.stage1.getLaunchTarget();
    }

    @Override
    public String[] getLaunchArguments() {
        return this.stage1.getLaunchArguments();
    }

    public static boolean isRequired() {
        @SuppressWarnings("unchecked")
        List<ITweaker> currentCycle = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        return currentCycle.stream().anyMatch(it -> it.getClass().getName().equals(FML_TWEAKER));
    }

    public static void prepare(ITweaker stage0) {
        if (realStage0 != null) {
            throw new IllegalStateException("Can only delay one stage0 tweaker. Why are there multiple anyway?");
        }
        realStage0 = stage0;

        String commandLineCoremodsStr = System.getProperty(COMMAND_LINE_COREMODS_PROP, "");
        commandLineCoremods = commandLineCoremodsStr.isEmpty() ? new String[0] : commandLineCoremodsStr.split(",");
        System.clearProperty(COMMAND_LINE_COREMODS_PROP);
    }

    public static void inject() {
        @SuppressWarnings("unchecked")
        List<String> nextCycle = (List<String>) Launch.blackboard.get("TweakClasses");
        nextCycle.add(DelayedStage0Tweaker.class.getName());

        // Tweaker arguments are consumed by Launch.launch, so when relaunching we assume the FMLTweaker to be the only
        // one passed in (as common for production). However, if we end up here, then the Essential tweaker has also
        // been passed (as common for dev) next to the FMLTweaker (rather than being chain-loaded by it).
        // If we do not re-add ourselves to the tweaker list when we re-launch, then we may not get called at all (or
        // too late if there are command line supplied coremods relying on us), so we add ourselves to the launchArgs
        // which FMLTweaker makes available (and which we use in Relaunch to take an educated guess at the original
        // arguments).
        @SuppressWarnings("unchecked")
        Map<String, String> launchArgs = (Map<String, String>) Launch.blackboard.get("launchArgs");
        String prevValue = launchArgs.put("--tweakClass", "gg.essential.loader.stage0.EssentialSetupTweaker");
        if (prevValue != null) {
            throw new UnsupportedOperationException("Cannot re-register Essential tweaker because \""
                + prevValue + "\" was already there. This will require a more complex implementation.");
        }
    }
}
