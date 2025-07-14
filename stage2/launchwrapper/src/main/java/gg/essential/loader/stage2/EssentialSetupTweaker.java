package gg.essential.loader.stage2;

import gg.essential.loader.stage2.util.Stage0Tracker;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

public class EssentialSetupTweaker implements ITweaker {
    public static final RelaunchedLoader LOADER;
    static {
        RelaunchInfo relaunchInfo = RelaunchInfo.get();
        if (relaunchInfo == null) {
            Loader loader = new Loader();
            loader.loadAndRelaunch();
            throw new AssertionError("relaunch should not return");
        } else {
            LOADER = new RelaunchedLoader(relaunchInfo);
        }
    }

    public EssentialSetupTweaker(ITweaker stage0) {
        LOADER.initialize(stage0);
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        Stage0Tracker.registerStage0Tweaker();

        LOADER.injectIntoClassLoader(classLoader);
    }

    @Override
    public String getLaunchTarget() {
        return "";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
