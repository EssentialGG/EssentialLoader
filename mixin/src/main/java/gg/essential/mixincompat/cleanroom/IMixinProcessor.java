package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatMixin;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.service.IMixinService;

import java.util.List;

// Interface added in Cleanroom's Mixin fork to expose certain internals
// https://github.com/CleanroomMC/MixinBooter-UniMix/blob/9d4b487ed32501137645cdf0da484b076f0bfaf4/src/main/java/org/spongepowered/asm/mixin/extensibility/IMixinProcessor.java
@CompatMixin(target = "org.spongepowered.asm.mixin.extensibility.IMixinProcessor", createTarget = true)
public interface IMixinProcessor {
    IMixinService getMixinService();
    List<IMixinConfig> getMixinConfigs();
    List<IMixinConfig> getPendingMixinConfigs();
}
