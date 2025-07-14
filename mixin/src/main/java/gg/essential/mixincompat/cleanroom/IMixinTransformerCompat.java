package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatMixin;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

@CompatMixin(IMixinTransformer.class)
public interface IMixinTransformerCompat {
    // Method which exists in Cleanroom's Mixin fork, and MixinBooter crashes if it's missing
    // https://github.com/CleanroomMC/MixinBooter/blob/05fc6c7b4b36a714c90eb4cc2f2364c681da0bc8/src/main/java/zone/rong/mixinbooter/mixin/LoadControllerMixin.java#L115
    default IMixinProcessor getProcessor() { throw new UnsupportedOperationException(); }
}
