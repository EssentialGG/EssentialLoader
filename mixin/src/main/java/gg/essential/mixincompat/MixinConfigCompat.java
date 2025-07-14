package gg.essential.mixincompat;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import gg.essential.mixincompat.extensions.MixinConfigExt;
import org.spongepowered.asm.util.VersionNumber;

@CompatMixin(target = "org.spongepowered.asm.mixin.transformer.MixinConfig")
public class MixinConfigCompat implements MixinConfigExt {
    @CompatShadow
    private String version;

    @Override
    public VersionNumber getMinVersion() {
        return VersionNumber.parse(this.version);
    }
}
