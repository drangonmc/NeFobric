package net.fabricmc.loader.api;

/** 创建或调用入口时出错。 */
@SuppressWarnings("serial")
public class EntrypointException extends RuntimeException {
    private final String key;

    @Deprecated
    public EntrypointException(String key, Throwable cause) {
        super("Exception while loading entries for entrypoint '" + key + "'!", cause);
        this.key = key;
    }

    @Deprecated
    public EntrypointException(String key, String causingMod, Throwable cause) {
        super("Exception while loading entries for entrypoint '" + key + "' provided by '" + causingMod + "'", cause);
        this.key = key;
    }

    @Deprecated
    public EntrypointException(String s) {
        super(s);
        this.key = "";
    }

    @Deprecated
    public EntrypointException(Throwable t) {
        super(t);
        this.key = "";
    }

    public String getKey() {
        return key;
    }
}
