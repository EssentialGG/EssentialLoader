package gg.essential.loader.stage2.relaunch.mixin;

import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.lang.reflect.Constructor;
import java.util.function.BiFunction;

/**
 * Transformer which applies Mixins to the given bytecode.
 */
class RelaunchTransformer implements BiFunction<String, byte[], byte[]> {
    private final IMixinTransformer mixinTransformer;

    public RelaunchTransformer() throws ReflectiveOperationException {
        Constructor<?> constructor =
            Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
        constructor.setAccessible(true);
        this.mixinTransformer = (IMixinTransformer) constructor.newInstance();
    }

    @Override
    public byte[] apply(String name, byte[] bytes) {
        return this.mixinTransformer.transformClassBytes(name, name, bytes);
    }
}
