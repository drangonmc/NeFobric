package net.minecraftforge.registries;

/** 加载完成后可以锁定、不再接受新内容的注册表。 */
public interface ILockableRegistry {
    void lock();
}
