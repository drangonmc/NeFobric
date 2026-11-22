package dev.nefobric.testmods.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.FakeMinecraft")
public abstract class ClientGameMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void forgeHello$constructed(String[] args, CallbackInfo ci) {
        System.out.println("[forge_hello] mixin: FakeMinecraft constructed");
    }
}
