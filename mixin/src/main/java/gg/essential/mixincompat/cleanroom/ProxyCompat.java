package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatAccessTransformer;
import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import gg.essential.mixincompat.MixinTransformerCompat;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.transformer.Proxy;

@CompatMixin(Proxy.class)
public class ProxyCompat {
    // This field is public in Cleanroom's Mixin fork, and MixinBooter accesses it as such
    // https://github.com/CleanroomMC/MixinBooter/blob/05fc6c7b4b36a714c90eb4cc2f2364c681da0bc8/src/main/java/zone/rong/mixinbooter/mixin/LoadControllerMixin.java#L115
    @CompatShadow
    @CompatAccessTransformer(add = Opcodes.ACC_PUBLIC, remove = Opcodes.ACC_PRIVATE)
    private static MixinTransformerCompat transformer;
}
