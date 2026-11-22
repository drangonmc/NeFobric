package net.minecraftforge.fml.loading;

import net.minecraftforge.forgespi.language.IModInfo;

import java.util.List;

/** 加载早期（游戏开始前）发现的错误。NeFobric 在自己的启动流程里报告这些错误，这个类主要为签名兼容。 */
public class EarlyLoadingException extends RuntimeException {
    public static class ExceptionData {
        private final IModInfo modInfo;
        private final String i18message;
        private final Object[] args;

        public ExceptionData(final String message, Object... args) {
            this(message, null, args);
        }

        public ExceptionData(final String message, final IModInfo modInfo, Object... args) {
            this.i18message = message;
            this.modInfo = modInfo;
            this.args = args;
        }

        public String getI18message() {
            return i18message;
        }

        public Object[] getArgs() {
            return args;
        }

        public IModInfo getModInfo() {
            return modInfo;
        }
    }

    private final List<ExceptionData> errorMessages;

    public EarlyLoadingException(final String message, final Throwable originalException,
                                 List<ExceptionData> errorMessages) {
        super(message, originalException);
        this.errorMessages = errorMessages;
    }

    public List<ExceptionData> getAllData() {
        return errorMessages;
    }
}
