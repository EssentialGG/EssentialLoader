package gg.essential.mixincompat;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import gg.essential.mixincompat.util.MixinCompatUtils;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.modify.ModifyVariableInjector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;

import java.util.Locale;

@CompatMixin(ModifyVariableInjector.class)
public abstract class ModifyVariableInjectorCompat extends Injector {
    public ModifyVariableInjectorCompat(InjectionInfo info, String annotationType) {
        super(info, annotationType);
    }

    @CompatShadow
    private LocalVariableDiscriminator discriminator;

    @CompatShadow(original = "getTargetNodeKey")
    protected abstract String getTargetNodeKey$old(Target target, InjectionNodes.InjectionNode node);

    protected String getTargetNodeKey(Target target, InjectionNodes.InjectionNode node) {
        return MixinCompatUtils.withCurrentMixinInfo(
                this.info.getMixin().getMixin(),
                () -> String.format(
                        Locale.ROOT,
                        "localcontext(%s,%s,#%s,useNewAlgorithm=%s)",
                        this.returnType,
                        this.discriminator.isArgsOnly() ? "argsOnly" : "fullFrame",
                        node.getId(),
                        MixinCompatUtils.canUseNewLocalsAlgorithm()
                )
        );
    }
}
