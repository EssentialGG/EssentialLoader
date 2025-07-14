package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatMixin;
import org.spongepowered.asm.launch.GlobalProperties;

@CompatMixin(GlobalProperties.Keys.class)
public class GlobalPropertiesKeysCompat {
    // Required for MixinBooter to not crash on boot
    // https://github.com/CleanroomMC/MixinBooter/blob/05fc6c7b4b36a714c90eb4cc2f2364c681da0bc8/src/main/java/zone/rong/mixinbooter/MixinBooterPlugin.java#L87
    // https://github.com/CleanroomMC/MixinBooter/blob/05fc6c7b4b36a714c90eb4cc2f2364c681da0bc8/src/main/java/zone/rong/mixinbooter/MixinBooterPlugin.java#L198
    // https://github.com/CleanroomMC/MixinBooter-UniMix/blob/9d4b487ed32501137645cdf0da484b076f0bfaf4/src/main/java/org/spongepowered/asm/launch/GlobalProperties.java#L55
    public static final GlobalProperties.Keys CLEANROOM_DISABLE_MIXIN_CONFIGS = GlobalProperties.Keys.of("mixin.cleanroom.disablemixinconfigs");
}
