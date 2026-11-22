package dev.nefobric.testmods.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 改模拟游戏的客户端游戏对象（真正的模组会用 @Mixin(Minecraft.class)，这里用字符串避免编译时依赖假游戏）。 */
@Mixin(targets = "net.minecraft.client.FakeMinecraft")
public abstract class ClientGameMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void fabricHello$beforeRun(CallbackInfo ci) {
        System.out.println("[fabric_hello] mixin: before FakeMinecraft.run");
    }
}
