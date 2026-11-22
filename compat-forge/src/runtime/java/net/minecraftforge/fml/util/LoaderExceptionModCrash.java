package net.minecraftforge.fml.util;

/** 模组代码出错导致的加载失败。 */
public class LoaderExceptionModCrash extends LoaderException {
    public LoaderExceptionModCrash(String message, Throwable cause) {
        super(message, cause);
    }
}
