package dev.nefobric.loader.spi;

import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.util.Messages;

/** 某个模组在初始化时出错。消息里带上模组和出错的入口，方便玩家定位。 */
public class ModLoadingException extends Exception {
    private final transient ModCandidate mod;
    private final String where;

    public ModLoadingException(ModCandidate mod, String where, Throwable cause) {
        super(Messages.tr("load.failed", mod.describe(), where, String.valueOf(cause)), cause);
        this.mod = mod;
        this.where = where;
    }

    public ModCandidate mod() {
        return mod;
    }

    public String where() {
        return where;
    }
}
