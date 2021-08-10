package gg.essential.impl.mixins;

import gg.essential.impl.EssentialMod;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MixinMain {
    @Inject(method = "main", at = @At("HEAD"))
    private static void initEssential(String[] args, CallbackInfo ci) {
        EssentialMod.mixinWorking = true;
    }
}
