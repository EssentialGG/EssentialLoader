package gg.essential.loader.stage2.compat.tweaker;

import com.google.common.collect.ImmutableSet;
import gg.essential.loader.stage2.compat.BetterFpsTransformerWrapper;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

public class BetterFpsWrappingTweaker implements ITweaker {
    private static final Set<String> BROKEN_TRANSFORMERS = ImmutableSet.of(
        "me.guichaguri.betterfps.transformers.EventTransformer",
        "me.guichaguri.betterfps.transformers.MathTransformer"
    );

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        try {
            Field transformersField = LaunchClassLoader.class.getDeclaredField("transformers");
            transformersField.setAccessible(true);
            List<IClassTransformer> transformers = (List<IClassTransformer>) transformersField.get(classLoader);

            for (int i = 0; i < transformers.size(); i++) {
                IClassTransformer transformer = transformers.get(i);
                if (BROKEN_TRANSFORMERS.contains(transformer.getClass().getName())) {
                    transformers.set(i, new BetterFpsTransformerWrapper(transformer));
                }
            }
        } catch (Throwable e) {
            System.err.println("Failed to wrap BetterFPS' broken transformers! Chaos incoming...");
            e.printStackTrace();
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    @SuppressWarnings("unchecked")
    public static void inject() {
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
        tweakClasses.add(BetterFpsWrappingTweaker.class.getName());
    }
}
