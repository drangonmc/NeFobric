package dev.nefobric.loader.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * NeFobric 自己的日志。
 *
 * <p>加载器在游戏的日志库就绪之前就要输出信息（专用服务端甚至要先解出日志库），所以不用 log4j / slf4j，
 * 直接写到启动时的标准输出，并同时写入 {@code logs/nefobric-latest.log}。原版在 Bootstrap 之后会替换
 * {@code System.out}，这里保存的是替换前的那个流，避免日志被套上两层前缀。
 */
public final class Log {
    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final PrintStream OUT = System.out;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final boolean DEBUG = Boolean.getBoolean("nefobric.debug");
    private static final List<String> PENDING = new ArrayList<>();
    private static Writer file;
    private static Path filePath;

    private Log() {
    }

    /** 打开日志文件，并把打开之前已经输出的内容补写进去。 */
    public static synchronized void openFile(Path path) {
        if (file != null) {
            return;
        }
        try {
            Path absolute = path.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            file = Files.newBufferedWriter(absolute, StandardCharsets.UTF_8);
            filePath = absolute;
            for (String chunk : PENDING) {
                file.write(chunk);
            }
            PENDING.clear();
            file.flush();
        } catch (IOException e) {
            file = null;
            filePath = null;
            warn("Cannot open log file " + path + ": " + e);
        }
    }

    public static synchronized Path filePath() {
        return filePath;
    }

    public static boolean isDebug() {
        return DEBUG;
    }

    public static void debug(String message) {
        if (DEBUG) {
            log(Level.DEBUG, message, null);
        }
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void warn(String message, Throwable t) {
        log(Level.WARN, message, t);
    }

    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    public static void error(String message, Throwable t) {
        log(Level.ERROR, message, t);
    }

    public static void log(Level level, String message, Throwable t) {
        log(level, "NeFobric", message, t);
    }

    /** {@code source} 是方括号里的来源名，例如 Mixin 的日志名 “mixin”。 */
    public static synchronized void log(Level level, String source, String message, Throwable t) {
        String prefix = "[" + LocalTime.now().format(TIME) + "] [" + Thread.currentThread().getName()
                + "/" + level + "] [" + source + "]: ";
        StringBuilder sb = new StringBuilder();
        for (String line : message.split("\\R", -1)) {
            sb.append(prefix).append(line).append(System.lineSeparator());
        }
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        String text = sb.toString();
        OUT.print(text);
        OUT.flush();
        if (file != null) {
            try {
                file.write(text);
                file.flush();
            } catch (IOException ignored) {
                // 日志文件写不进去时只保留控制台输出
            }
        } else {
            PENDING.add(text);
        }
    }
}
