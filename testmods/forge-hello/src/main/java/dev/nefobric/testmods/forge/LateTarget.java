package dev.nefobric.testmods.forge;

/** late_at.cfg 的目标：运行中追加的访问变换把 secret 改成 public。 */
@SuppressWarnings("unused")
final class LateTarget {
    private LateTarget() {
    }

    private static String secret() {
        return "late";
    }
}
