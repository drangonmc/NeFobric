package dev.nefobric.loader.metadata;

import dev.nefobric.loader.util.Messages;

/** 模组来自哪个生态。 */
public enum Ecosystem {
    /** 加载器自己提供的虚拟模组，例如 minecraft、java、forge、fabricloader。 */
    BUILTIN("eco.builtin"),
    FABRIC("eco.fabric"),
    FORGE("eco.forge"),
    NEOFORGE("eco.neoforge");

    private final String key;

    Ecosystem(String key) {
        this.key = key;
    }

    /** 1.20.1 上 Forge 47 与 NeoForge 47.1 走同一个兼容层。 */
    public boolean isForgeFamily() {
        return this == FORGE || this == NEOFORGE;
    }

    public String displayName() {
        return Messages.tr(key);
    }
}
