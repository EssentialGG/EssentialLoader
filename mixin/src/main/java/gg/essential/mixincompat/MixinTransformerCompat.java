package gg.essential.mixincompat;

import gg.essential.CompatAccessTransformer;
import gg.essential.CompatMixin;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Method;

// Accessed from fml.common.Loader by Inject from some 1.12.2 mods, e.g. mixin 0.7 versions of VanillaFix and JEID
@CompatAccessTransformer(add = {Opcodes.ACC_PUBLIC})
@CompatMixin(target = "org.spongepowered.asm.mixin.transformer.MixinTransformer")
public abstract class MixinTransformerCompat {
    //
    // These two are called via reflection by quite a few old (mixin 0.7) mods on 1.12.2, e.g. VanillaFix and JEID
    // They were moved to MixinProcessor in Mixin 0.8, so we'll forward those calls.
    //

    private void selectConfigs(MixinEnvironment environment) {
        invokeOnProcessor("selectConfigs", environment);
    }

    private int prepareConfigs(MixinEnvironment environment) {
        return (int) invokeOnProcessor("prepareConfigs", environment);
    }

    private Object invokeOnProcessor(String methodName, MixinEnvironment environment) {
        // It's all package private, so reflection it is
        try {
            Object processor = getClass().getDeclaredField("processor").get(this);
            Method method = processor.getClass().getDeclaredMethod(methodName, MixinEnvironment.class);
            method.setAccessible(true);
            return method.invoke(processor, environment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
