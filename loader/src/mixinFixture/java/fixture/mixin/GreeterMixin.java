package fixture.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fixture.target.Greeter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Greeter.class)
public abstract class GreeterMixin {
    /** 普通的 @Inject。 */
    @Inject(method = "greet", at = @At("RETURN"), cancellable = true)
    private void fixture$exclaim(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue() + "!");
    }

    /** MixinExtras。 */
    @ModifyReturnValue(method = "count", at = @At("RETURN"))
    private int fixture$double(int original) {
        return original * 2;
    }

    /** @ModifyArgs 会让 Mixin 在运行时生成一个合成类 org.spongepowered.asm.synthetic.args.Args$N。 */
    @ModifyArgs(method = "join", at = @At(value = "INVOKE",
            target = "Lfixture/target/Greeter;concat(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
    private void fixture$swap(Args args) {
        String a = args.get(0);
        args.set(0, args.get(1));
        args.set(1, a);
    }
}
