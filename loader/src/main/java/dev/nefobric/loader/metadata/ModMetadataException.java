package dev.nefobric.loader.metadata;

/** 模组元数据无法解析。消息已经是给玩家看的文字。 */
public class ModMetadataException extends Exception {
    public ModMetadataException(String message) {
        super(message);
    }

    public ModMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
