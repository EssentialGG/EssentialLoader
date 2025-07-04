package gg.essential.mixincompat;

import gg.essential.CompatAccessTransformer;
import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;


@CompatAccessTransformer(add = {Opcodes.ACC_PUBLIC})
@CompatMixin(target = "org.spongepowered.asm.mixin.transformer.MixinProcessor")
public class MixinProcessorCompat {
    @CompatShadow
    private Extensions extensions;

    @CompatShadow
    private int prepareConfigs(MixinEnvironment environment, Extensions extensions) {
        throw new LinkageError();
    }

    // Used via reflection by quite a few mods on 1.12.2, e.g. VanillaFix
    private int prepareConfigs(MixinEnvironment environment) {
        return prepareConfigs(environment, this.extensions);
    }
}
