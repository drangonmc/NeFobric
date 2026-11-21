package net.minecraft.client;

/** 对应原版的 SilentInitException。 */
public class FakeSilentInitException extends RuntimeException {
    public FakeSilentInitException(String message) {
        super(message);
    }
}
