package dev.nefobric.testmods.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.Main")
public abstract class ServerMainMixin {
    @Inject(method = "main", at = @At("HEAD"))
    private static void fabricHello$onMain(String[] args, CallbackInfo ci) {
        System.out.println("[fabric_hello] mixin: server main");
    }
}
