package net.minecraftforge.fml;

import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组更新检查。NeFobric 不联网检查更新（游戏启动时不应该偷偷访问模组作者的服务器），所有模组都是
 * {@link Status#PENDING}，和 Forge 关闭了更新检查时一样。
 */
public class VersionChecker {
    public enum Status {
        PENDING(),
        FAILED(),
        UP_TO_DATE(),
        OUTDATED(3, true),
        AHEAD(),
        BETA(),
        BETA_OUTDATED(6, true);

        final int sheetOffset;
        final boolean draw;
        final boolean animated;

        Status() {
            this(0, false, false);
        }

        Status(int sheetOffset) {
            this(sheetOffset, true, false);
        }

        Status(int sheetOffset, boolean animated) {
            this(sheetOffset, true, animated);
        }

        Status(int sheetOffset, boolean draw, boolean animated) {
            this.sheetOffset = sheetOffset;
            this.draw = draw;
            this.animated = animated;
        }

        public int getSheetOffset() {
            return sheetOffset;
        }

        public boolean shouldDraw() {
            return draw;
        }

        public boolean isAnimated() {
            return animated;
        }

        public boolean isOutdated() {
            return this == OUTDATED || this == BETA_OUTDATED;
        }
    }

    public record CheckResult(VersionChecker.Status status, ComparableVersion target,
                              Map<ComparableVersion, String> changes, String url) {
    }

    private static final CheckResult PENDING_CHECK = new CheckResult(Status.PENDING, null, Map.of(), null);
    private static final Map<IModInfo, CheckResult> results = new ConcurrentHashMap<>();

    public static void startVersionCheck() {
        // 不联网
    }

    public static CheckResult getResult(IModInfo mod) {
        return results.getOrDefault(mod, PENDING_CHECK);
    }
}
