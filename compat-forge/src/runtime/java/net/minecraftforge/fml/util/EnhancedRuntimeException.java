package net.minecraftforge.fml.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 带附加说明的运行时异常：子类在 {@link #printStackTrace(WrappedPrintStream)} 里写额外的几行，
 * 打印堆栈时排在前面；被 log4j 记录时也会并进消息里。
 */
public abstract class EnhancedRuntimeException extends RuntimeException {
    public EnhancedRuntimeException() {
    }

    public EnhancedRuntimeException(String message) {
        super(message);
    }

    public EnhancedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public EnhancedRuntimeException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length > 2 && stack[2].getClassName().startsWith("org.apache.logging.log4j.")) {
            StringWriter buf = new StringWriter();
            String msg = super.getMessage();
            if (msg != null) {
                buf.append(msg);
            }
            buf.append('\n');
            printStackTrace(new WrappedPrintStream() {
                @Override
                public void println(String line) {
                    buf.append(line).append('\n');
                }
            });
            return buf.toString();
        }
        return super.getMessage();
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        printStackTrace(new WrappedPrintStream() {
            @Override
            public void println(String line) {
                s.println(line);
            }
        });
        super.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        printStackTrace(new WrappedPrintStream() {
            @Override
            public void println(String line) {
                s.println(line);
            }
        });
        super.printStackTrace(s);
    }

    protected abstract void printStackTrace(WrappedPrintStream stream);

    public abstract static class WrappedPrintStream {
        public abstract void println(String line);
    }
}
