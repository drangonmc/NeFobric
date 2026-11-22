package dev.nefobric.compat.fabric.transform;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;

/** 测试用：部分成员和一个接口只在客户端存在。 */
@EnvironmentInterface(value = EnvType.CLIENT, itf = Runnable.class)
@EnvironmentInterface(value = EnvType.SERVER, itf = AutoCloseable.class)
public class EnvMixed implements Runnable, AutoCloseable, java.io.Serializable {
    @Environment(EnvType.CLIENT)
    public int clientField;

    public int commonField;

    @Environment(EnvType.CLIENT)
    @Override
    public void run() {
    }

    @Environment(EnvType.SERVER)
    @Override
    public void close() {
    }

    public void common() {
    }
}
