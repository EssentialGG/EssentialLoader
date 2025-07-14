package gg.essential.mixincompat.cleanroom;

import gg.essential.CompatMixin;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

@CompatMixin(ClassInfo.class)
public class ClassInfoCompat {
    // Method added in Cleanroom's Mixin fork, and MixinBooter calls it
    // https://github.com/CleanroomMC/MixinBooter/blob/05fc6c7b4b36a714c90eb4cc2f2364c681da0bc8/src/main/java/zone/rong/mixinbooter/fix/MixinFixer.java#L35
    // https://github.com/CleanroomMC/MixinBooter-UniMix/blob/9d4b487ed32501137645cdf0da484b076f0bfaf4/src/main/java/org/spongepowered/asm/mixin/transformer/ClassInfo.java#L2239
    public static void registerCallback(Callback callback) {
        // It seems like MixinBooter uses these callbacks to replace init-phase mixins targeting Forge's Loader to
        // make it possible to target ordinary mod classes via mixins.
        // Given we already add back in the methods which those old mixins call, it should be fine for us to just
        // allow them to apply as they also would without MixinBooter, and it should just work.
        // This method can therefore just do nothing.
    }

    @CompatMixin(target = "org.spongepowered.asm.mixin.transformer.ClassInfo$Callback", createTarget = true)
    public interface Callback {
        void onInit(ClassInfo classInfo);
    }
}
