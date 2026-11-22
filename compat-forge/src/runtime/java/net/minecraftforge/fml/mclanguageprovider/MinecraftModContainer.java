package net.minecraftforge.fml.mclanguageprovider;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.forgespi.language.IModInfo;

/** 模组列表里 {@code minecraft} 这个模组的容器。它没有模组总线，生命周期事件直接忽略。 */
public class MinecraftModContainer extends ModContainer {
    private final Object minecraftmod = new Object() {
        @Override
        public String toString() {
            return "minecraft";
        }
    };

    public MinecraftModContainer(IModInfo info) {
        super(info);
        this.contextExtension = () -> null;
    }

    @Override
    public boolean matches(Object mod) {
        return mod == minecraftmod;
    }

    @Override
    public Object getMod() {
        return minecraftmod;
    }
}
