package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.Config;

import java.util.Collections;
import java.util.Set;

@CompatMixin(target = "org.spongepowered.asm.mixin.transformer.MixinConfig")
public class MixinConfigCompat {
    // See GlobalPropertiesKeysCompat
    // https://github.com/CleanroomMC/MixinBooter-UniMix/blob/9d4b487ed32501137645cdf0da484b076f0bfaf4/src/main/java/org/spongepowered/asm/mixin/transformer/MixinConfig.java#L1400
    static Config create(String configFile, MixinEnvironment outer) {
        Set<String> disabledMixinConfigs = GlobalProperties.get(GlobalPropertiesKeysCompat.CLEANROOM_DISABLE_MIXIN_CONFIGS, Collections.emptySet());
        if (disabledMixinConfigs.contains(configFile)) {
            return null;
        }
        return create$original(configFile, outer);
    }

    @CompatShadow(original = "create")
    static Config create$original(String configFile, MixinEnvironment outer) { throw new LinkageError(); }
}
