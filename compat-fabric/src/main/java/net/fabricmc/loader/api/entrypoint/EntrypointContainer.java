package net.fabricmc.loader.api.entrypoint;

import net.fabricmc.loader.api.ModContainer;

/** 入口对象和提供它的模组。 */
public interface EntrypointContainer<T> {
    T getEntrypoint();

    ModContainer getProvider();

    /** fabric.mod.json 里写的入口字符串。 */
    default String getDefinition() {
        return "";
    }
}
