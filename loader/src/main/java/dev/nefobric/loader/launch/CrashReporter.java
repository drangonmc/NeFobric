package dev.nefobric.loader.launch;

import dev.nefobric.loader.NeFobric;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.spi.ModLoadingException;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 出错时给玩家的说明：控制台里一块醒目的文字，加上 {@code crash-reports/nefobric-*.txt}。
 * 报告里明确写出 NeFobric，引导玩家向 NeFobric 而不是模组原作者报告问题（架构文档的风险应对）。
 */
public final class CrashReporter {
    private static final String RULE = "============================================================";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private static volatile Path gameDir;
    private static volatile List<ModCandidate> knownMods = List.of();

    private CrashReporter() {
    }

    static void setContext(Path dir, List<ModCandidate> mods) {
        gameDir = dir;
        knownMods = List.copyOf(mods);
    }

    /** 启动前发现的问题。返回写出的报告路径（可能为 null）。 */
    public static Path startupFailure(List<String> problems) {
        StringBuilder body = new StringBuilder();
        body.append(Messages.tr("fatal.count", problems.size())).append('\n');
        for (int i = 0; i < problems.size(); i++) {
            String text = problems.get(i).replace("\n", "\n     ");
            body.append(String.format(" %2d. %s%n", i + 1, text));
        }
        return report(Messages.tr("fatal.title"), body.toString(), null);
    }

    public static Path unexpected(Throwable t) {
        return report(Messages.tr("fatal.title"), Messages.tr("fatal.unexpected", String.valueOf(t)) + "\n", t);
    }

    public static Path modFailure(ModLoadingException e, NeFobricLoader loader) {
        return report(Messages.tr("fatal.modTitle"), e.getMessage() + "\n", e.getCause());
    }

    private static Path report(String title, String body, Throwable t) {
        Path file = writeFile(title, body, t);
        StringBuilder sb = new StringBuilder();
        sb.append(RULE).append('\n');
        sb.append(title).append('\n');
        sb.append(RULE).append('\n');
        sb.append(body);
        if (Log.filePath() != null) {
            sb.append(Messages.tr("fatal.logHint", Log.filePath())).append('\n');
        }
        if (file != null) {
            sb.append(Messages.tr("fatal.crashHint", file)).append('\n');
        }
        sb.append(Messages.tr("fatal.reportHint")).append('\n');
        sb.append(RULE);
        Log.error(sb.toString(), t);
        return file;
    }

    private static Path writeFile(String title, String body, Throwable t) {
        Path dir = gameDir;
        if (dir == null) {
            return null;
        }
        try {
            Path reports = dir.resolve("crash-reports");
            Files.createDirectories(reports);
            Path file = reports.resolve("nefobric-" + LocalDateTime.now().format(FILE_TIME) + ".txt");
            StringBuilder sb = new StringBuilder();
            sb.append("---- NeFobric ").append(NeFobric.VERSION).append(" crash report ----\n");
            sb.append("Time: ").append(LocalDateTime.now()).append('\n');
            sb.append("Minecraft target: ").append(NeFobric.TARGET_MINECRAFT).append('\n');
            sb.append("Java: ").append(System.getProperty("java.version")).append(" (")
                    .append(System.getProperty("java.vendor")).append(")\n");
            sb.append("OS: ").append(System.getProperty("os.name")).append(' ')
                    .append(System.getProperty("os.version")).append(" (").append(System.getProperty("os.arch"))
                    .append(")\n\n");
            sb.append(title).append("\n\n").append(body).append('\n');
            sb.append("Mods:\n");
            for (ModCandidate m : knownMods) {
                sb.append("  - ").append(m.describe()).append('\n');
            }
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                sb.append("\nStack trace:\n").append(sw);
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            Log.warn("Cannot write crash report: " + e);
            return null;
        }
    }
}
