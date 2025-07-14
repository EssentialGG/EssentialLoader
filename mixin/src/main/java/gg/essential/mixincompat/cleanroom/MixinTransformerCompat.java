package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;

@CompatMixin(target = "org.spongepowered.asm.mixin.transformer.MixinTransformer")
public class MixinTransformerCompat {
    @CompatShadow
    private MixinProcessorCompat processor;

    // Overrides the interface method added in IMixinTransformerCompat
    public IMixinProcessor getProcessor() {
        return processor;
    }
}
