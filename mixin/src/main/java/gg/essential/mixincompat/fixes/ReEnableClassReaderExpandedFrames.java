package gg.essential.mixincompat.fixes;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

@CompatMixin(MixinEnvironment.Option.class)
public class ReEnableClassReaderExpandedFrames {
    //
    // Re-enables EXPAND_FRAMES by default because different Locals are generated without it, breaking existing mixins.
    // See https://github.com/SpongePowered/Mixin/issues/671
    //
    private boolean getLocalBooleanValue(boolean defaultValue) {
        //noinspection ConstantValue
        if ((Object) this == MixinEnvironment.Option.CLASSREADER_EXPAND_FRAMES) {
            defaultValue = true;
        }
        return getLocalBooleanValue$org(defaultValue);
    }
    @CompatShadow(original = "getLocalBooleanValue")
    private boolean getLocalBooleanValue$org(boolean defaultValue) { throw new LinkageError(); }
}
