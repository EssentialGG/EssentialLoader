package com.example.mod.mixin;

import sun.com.example.mod.LoadState;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MixinMain {
    @Inject(method = "main", at = @At("HEAD"))
    private static void initExampleMod(String[] args, CallbackInfo ci) {
        LoadState.coreMod = true;
    }
}
