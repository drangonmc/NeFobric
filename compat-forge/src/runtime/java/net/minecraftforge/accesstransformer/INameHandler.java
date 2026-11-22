package net.minecraftforge.accesstransformer;

/**
 * 读 AT 文件时把名字换成运行时的名字。NeFobric 和 Forge 正式环境一样直接用 SRG 名，默认不换。
 */
public interface INameHandler {
    String translateClassName(String className);

    String translateFieldName(String fieldName);

    String translateMethodName(String methodName);
}
