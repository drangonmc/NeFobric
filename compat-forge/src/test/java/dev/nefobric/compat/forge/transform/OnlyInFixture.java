package dev.nefobric.compat.forge.transform;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Supplier;

/** OnlyInStripperTest 的目标类：客户端专用的字段、方法、接口。 */
@OnlyIn(value = Dist.CLIENT, _interface = Runnable.class)
@SuppressWarnings("unused")
class OnlyInFixture implements Runnable, Comparable<OnlyInFixture> {
    @OnlyIn(Dist.CLIENT)
    private Object clientField;
    private Object commonField;

    @OnlyIn(Dist.CLIENT)
    public Supplier<Supplier<String>> clientMethod() {
        return () -> () -> "client";
    }

    @OnlyIn(Dist.DEDICATED_SERVER)
    public void serverMethod() {
    }

    public Supplier<String> commonMethod() {
        return () -> "common";
    }

    @Override
    public void run() {
    }

    @Override
    public int compareTo(OnlyInFixture o) {
        return 0;
    }

    @OnlyIn(Dist.CLIENT)
    static final class ClientOnly {
    }
}
