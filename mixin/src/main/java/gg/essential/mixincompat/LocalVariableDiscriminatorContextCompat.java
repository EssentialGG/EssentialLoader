package gg.essential.mixincompat;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import gg.essential.mixincompat.util.MixinCompatUtils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;

@CompatMixin(LocalVariableDiscriminator.Context.class)
public class LocalVariableDiscriminatorContextCompat {
    @CompatShadow
    InjectionInfo info;

    @CompatShadow(original = "initLocals")
    private LocalVariableDiscriminator.Context.Local[] initLocals$original(Target target, boolean argsOnly, AbstractInsnNode node) { throw new LinkageError(); }

    private LocalVariableDiscriminator.Context.Local[] initLocals(Target target, boolean argsOnly, AbstractInsnNode node) {
        return MixinCompatUtils.withCurrentMixinInfo(this.info.getMixin().getMixin(), () -> initLocals$original(target, argsOnly, node));
    }
}
