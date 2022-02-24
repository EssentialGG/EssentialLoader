package com.example.mod.mixin.init;

import net.minecraftforge.fml.common.Loader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.com.example.mod.LoadState;

@Mixin(Loader.class)
public class MixinLoader {
    @Inject(method = "injectData", at = @At("HEAD"))
    private static void exampleInject(CallbackInfo ci) {
        LoadState.checkForRelaunch();
        LoadState.mixinInitPhase = true;
    }
}
