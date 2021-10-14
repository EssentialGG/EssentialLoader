package gg.essential.loader.stage2.relaunch.mixins.forge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.minecraftforge.fml.common.launcher.FMLTweaker", remap = false)
public class Mixin_SkipFMLSecurityManager {
    // Fuck forge
    // It installs a SecurityManager which locks itself down by rejecting any future managers and forge
    // itself refuses to boot if its manager is rejected (e.g. by a manager previously installed by it).
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/System;setSecurityManager(Ljava/lang/SecurityManager;)V"))
    private void skipSecurityManager(SecurityManager securityManager) {
    }
}
