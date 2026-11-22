package net.minecraftforge.fml.loading;

import org.slf4j.Logger;

import java.io.PrintStream;

/** 把 {@code System.out}/{@code System.err} 的输出转进日志，并注明是哪个类、哪一行打印的。 */
public class TracingPrintStream extends PrintStream {
    private static final int BASE_DEPTH = 3;
    private final Logger logger;

    public TracingPrintStream(Logger logger, PrintStream original) {
        super(original);
        this.logger = logger;
    }

    @Override
    public void println(Object o) {
        log(String.valueOf(o));
    }

    @Override
    public void println(String s) {
        log(s);
    }

    @Override
    public void println(boolean x) {
        log(String.valueOf(x));
    }

    @Override
    public void println(char x) {
        log(String.valueOf(x));
    }

    @Override
    public void println(int x) {
        log(String.valueOf(x));
    }

    @Override
    public void println(long x) {
        log(String.valueOf(x));
    }

    @Override
    public void println(float x) {
        log(String.valueOf(x));
    }

    @Override
    public void println(double x) {
        log(String.valueOf(x));
    }

    @Override
    public void println(char[] x) {
        log(String.valueOf(x));
    }

    private void log(String s) {
        logger.info("{}{}", getPrefix(), s);
    }

    private String getPrefix() {
        StackTraceElement[] elems = Thread.currentThread().getStackTrace();
        StackTraceElement elem = elems[Math.min(BASE_DEPTH + 1, elems.length - 1)];
        if (elem.getClassName().startsWith("kotlin.io.")) {
            elem = elems[Math.min(BASE_DEPTH + 3, elems.length - 1)];
        } else if (elem.getClassName().startsWith("java.lang.Throwable")) {
            elem = elems[Math.min(BASE_DEPTH + 5, elems.length - 1)];
        }
        return "[" + elem.getClassName() + ":" + elem.getMethodName() + ":" + elem.getLineNumber() + "]: ";
    }
}
