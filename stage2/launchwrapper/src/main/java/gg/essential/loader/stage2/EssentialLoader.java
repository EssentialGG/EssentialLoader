package gg.essential.loader.stage2;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

public class EssentialLoader extends EssentialLoaderBase {
    private static final String MIXIN_TWEAKER = "org.spongepowered.asm.launch.MixinTweaker";

    public EssentialLoader(Path gameDir, String gameVersion) {
        super(gameDir, gameVersion);
    }

    @Override
    protected void loadPlatform() {
        try {
            injectMixinTweaker();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void addToClasspath(final File file) {
        try {
            final URL url = file.toURI().toURL();
            Launch.classLoader.addURL(url);

            final ClassLoader classLoader = Launch.classLoader.getClass().getClassLoader();
            final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    @Override
    protected boolean isInClassPath() {
        try {
            LinkedHashSet<String> objects = new LinkedHashSet<>();
            objects.add(CLASS_NAME);
            Launch.classLoader.clearNegativeEntries(objects);
            Class.forName(CLASS_NAME);
            return true;
        } catch (ClassNotFoundException ignored) { }
        return false;
    }

    // Production requires usage of the MixinTweaker. Simply calling MixinBootstrap.init() will not always work, even
    // if it appears to work most of the time.
    // This code is a intentional duplicate of the one in stage1. The one over there is in case the third-party mod
    // relies on Mixin and runs even when stage2 cannot be loaded, this one is for Essential and we do not want to mix
    // the two (e.g. we might change how this one works in the future but we cannot easily change the one in stage1).
    private static void injectMixinTweaker() throws ClassNotFoundException, IllegalAccessException, InstantiationException, IOException {
        @SuppressWarnings("unchecked")
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");

        // If the MixinTweaker is already queued (because of another mod), then there's nothing we need to to
        if (tweakClasses.contains(MIXIN_TWEAKER)) {
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
        Launch.classLoader.addClassLoaderExclusion(MIXIN_TWEAKER.substring(0, MIXIN_TWEAKER.lastIndexOf('.')));
        @SuppressWarnings("unchecked")
        List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        tweaks.add((ITweaker) Class.forName(MIXIN_TWEAKER, true, Launch.classLoader).newInstance());
    }
}
