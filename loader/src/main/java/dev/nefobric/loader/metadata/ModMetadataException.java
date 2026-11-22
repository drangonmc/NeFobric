package dev.nefobric.loader.metadata;

/**
 * 模组元数据无法解析。消息已经是给玩家看的文字。
 *
 * <p>用 {@link #skip(String)} 创建的异常表示“这个 jar 认得出来，但不是给当前游戏版本用的”
 * （例如面向 Minecraft 1.20.2 以后的 NeoForge 模组）：发现阶段只跳过这个 jar 并给出警告，
 * 不当成错误阻止启动。这和对 {@code neoforge.mods.toml}、Quilt 模组的处理一致。</p>
 */
public class ModMetadataException extends Exception {
    private final boolean skip;

    public ModMetadataException(String message) {
        this(message, null, false);
    }

    public ModMetadataException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private ModMetadataException(String message, Throwable cause, boolean skip) {
        super(message, cause);
        this.skip = skip;
    }

    /** 跳过这个 jar 并给出警告，而不是报错。{@code reason} 是给玩家看的原因。 */
    public static ModMetadataException skip(String reason) {
        return new ModMetadataException(reason, null, true);
    }

    /** 是否只需要跳过这个 jar。 */
    public boolean isSkip() {
        return skip;
    }
}
