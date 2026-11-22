package fixture.mixin;

import fixture.target.Greeter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Greeter.class)
public interface GreeterAccessor {
    @Accessor("name")
    String fixture$getName();

    @Accessor("name")
    void fixture$setName(String name);
}
