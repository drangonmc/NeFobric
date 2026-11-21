package dev.nefobric.loader.impl;

/**
 * 模组初始化失败时从游戏钩子里抛出的异常。客户端上原版会把它写进 “Initializing game” 崩溃报告。
 */
public class ModInitializationError extends RuntimeException {
    public ModInitializationError(String message, Throwable cause) {
        super(message, cause);
    }
}
