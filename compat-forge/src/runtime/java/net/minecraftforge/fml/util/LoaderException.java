package net.minecraftforge.fml.util;

/** 加载过程中的一般错误。 */
public class LoaderException extends EnhancedRuntimeException {
    public LoaderException() {
    }

    public LoaderException(String message) {
        super(message);
    }

    public LoaderException(String message, Throwable cause) {
        super(message, cause);
    }

    public LoaderException(Throwable cause) {
        super(cause);
    }

    @Override
    protected void printStackTrace(WrappedPrintStream stream) {
    }
}
