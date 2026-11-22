package dev.nefobric.testmods.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.Main")
public abstract class ServerMainMixin {
    @Inject(method = "main", at = @At("TAIL"))
    private static void forgeHello$afterMain(String[] args, CallbackInfo ci) {
        System.out.println("[forge_hello] mixin: server main returning");
    }
}
