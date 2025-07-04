package gg.essential.mixincompat;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

@CompatMixin(MixinServiceLaunchWrapper.class)
public abstract class MixinServiceLaunchWrapperCompat {
    @CompatShadow(original = "prepare")
    public abstract void prepare$org();

    public void prepare() {
        prepare$org();

        // Initialize our 0.7 asm compat transformer (see BundledAsmTransformer class)
        Launch.classLoader.addTransformerExclusion("gg.essential.lib.guava21.");
        Launch.classLoader.registerTransformer(BundledAsmTransformer.class.getName());
    }
}
