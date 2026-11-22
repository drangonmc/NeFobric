package net.fabricmc.loader.api;

import dev.nefobric.compat.fabric.entrypoint.DefaultLanguageAdapter;

/** 把 fabric.mod.json 里的入口字符串变成对象。模组可以在 languageAdapters 里注册自己的实现（例如 Kotlin）。 */
public interface LanguageAdapter {
    static LanguageAdapter getDefault() {
        return DefaultLanguageAdapter.INSTANCE;
    }

    <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException;
}
