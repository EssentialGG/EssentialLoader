package gg.essential.loader.stage2.util;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;

import java.util.List;

// Production requires usage of the MixinTweaker. Simply calling MixinBootstrap.init() will not always work, even
// if it appears to work most of the time.
// This code is a intentional duplicate of the one in stage1. The one over there is in case the third-party mod
// relies on Mixin and runs even when stage2 cannot be loaded, this one is for Essential and we do not want to mix
// the two (e.g. we might change how this one works in the future but we cannot easily change the one in stage1).
public class MixinTweakerInjector {
    private static final String MIXIN_TWEAKER = "org.spongepowered.asm.launch.MixinTweaker";

    public static void injectMixinTweaker(boolean canWait) {
        try {
            doInjectMixinTweaker(canWait);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void doInjectMixinTweaker(boolean canWait) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        @SuppressWarnings("unchecked")
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");

        // If the MixinTweaker is already queued (because of another mod), then there's nothing we need to to
        if (tweakClasses.contains(MIXIN_TWEAKER)) {
            if (!canWait) {
                // Except we do need to initialize the MixinTweaker immediately so we can add containers
                // for our mods.
                // This is idempotent, so we can call it without adding to the tweaks list (and we must not add to
                // it because the queued tweaker will already get added and there is nothing we can do about that).
                newMixinTweaker();
            }
            return;
        }

        // If it is already booted, we're also good to go
        if (Launch.blackboard.get("mixin.initialised") != null) {
            return;
        }

        System.out.println("Injecting MixinTweaker from EssentialLoader");

        // Otherwise, we need to take things into our own hands because the normal way to chainload a tweaker
        // (by adding it to the TweakClasses list during injectIntoClassLoader) is too late for Mixin.
        // Instead we instantiate the MixinTweaker on our own and add it to the current Tweaks list immediately.
        @SuppressWarnings("unchecked")
        List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        tweaks.add(newMixinTweaker());
    }

    private static ITweaker newMixinTweaker() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Launch.classLoader.addClassLoaderExclusion(MIXIN_TWEAKER.substring(0, MIXIN_TWEAKER.lastIndexOf('.')));
        return (ITweaker) Class.forName(MIXIN_TWEAKER, true, Launch.classLoader).newInstance();
    }
}
