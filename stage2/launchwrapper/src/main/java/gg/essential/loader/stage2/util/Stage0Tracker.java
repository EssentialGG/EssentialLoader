package gg.essential.loader.stage2.util;

import net.minecraft.launchwrapper.Launch;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Stage0Tracker {

    private static final String STAGE1_TWEAKER = "gg.essential.loader.stage1.EssentialSetupTweaker";
    private static final String STAGE0_TWEAKERS_KEY = "essential.loader.stage2.stage0tweakers";
    private static final Set<String> STAGE0_TWEAKERS = new HashSet<>();

    public static void registerStage0Tweaker() {
        Launch.blackboard.computeIfAbsent(STAGE0_TWEAKERS_KEY, k -> Collections.unmodifiableSet(STAGE0_TWEAKERS));

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stackTrace.length - 1; i++) {
            StackTraceElement element = stackTrace[i];
            if (element.getClassName().equals(STAGE1_TWEAKER) && element.getMethodName().equals("injectIntoClassLoader")) {
                STAGE0_TWEAKERS.add(stackTrace[i + 1].getClassName());
                break;
            }
        }
    }
}
