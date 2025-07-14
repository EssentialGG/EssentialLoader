package gg.essential.loader.stage1;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

// This class replaces the stage1 in a relaunched environment. It simply delegates to the stage2 which is already on
// the classpath at this point.
// If a more recent stage2 was discovered during loading, this allows us to directly use that, while the regular stage1
// would have loaded an older one from one of the mods in the mods folder.
@SuppressWarnings("unused") // called by stage0
public class EssentialSetupTweaker implements ITweaker {
    private final ITweaker stage2;

    public EssentialSetupTweaker(ITweaker stage0) {
        if (DelayedStage0Tweaker.isRequired()) {
            DelayedStage0Tweaker.prepare(stage0);
            this.stage2 = null;
            return;
        }

        this.stage2 = new gg.essential.loader.stage2.EssentialSetupTweaker(stage0);
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        if (this.stage2 == null) {
            return;
        }
        this.stage2.acceptOptions(args, gameDir, assetsDir, profile);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        if (this.stage2 == null) {
            DelayedStage0Tweaker.inject();
            return;
        }
        this.stage2.injectIntoClassLoader(classLoader);
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
