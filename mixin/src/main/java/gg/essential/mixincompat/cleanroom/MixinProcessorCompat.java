package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.service.IMixinService;

import java.util.Collections;
import java.util.List;

@CompatMixin(target = "org.spongepowered.asm.mixin.transformer.MixinProcessor")
public class MixinProcessorCompat implements IMixinProcessor {
    @CompatShadow
    private IMixinService service;
    @CompatShadow
    private List<IMixinConfig> configs;
    @CompatShadow
    private List<IMixinConfig> pendingConfigs;

    // See IMixinTransformerCompat
    @Override public IMixinService getMixinService() { return service; }
    @Override public List<IMixinConfig> getMixinConfigs() { return Collections.unmodifiableList(configs); }
    @Override public List<IMixinConfig> getPendingMixinConfigs() { return Collections.unmodifiableList(pendingConfigs); }
}
