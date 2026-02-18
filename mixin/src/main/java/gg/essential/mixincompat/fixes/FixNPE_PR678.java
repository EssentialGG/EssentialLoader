package gg.essential.mixincompat.fixes;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import org.spongepowered.asm.mixin.Mixins;

@CompatMixin(Mixins.class)
public class FixNPE_PR678 {
    //
    // Fixes regression in 0.8.6, see https://github.com/SpongePowered/Mixin/pull/678
    // Planned to be fixed upstream in 0.8.8.
    //
    public static void addConfiguration(String configFile) {
        Mixins.addConfiguration(configFile, null);
    }
    @CompatShadow(original = "addConfiguration")
    public static void addConfiguration$broken(String configFile) { throw new LinkageError(); }
}
