package net.minecraftforge.fml;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** 模组向 FML 注册的扩展点（Record）。 */
public interface IExtensionPoint<T extends Record> {
    /**
     * 服务器列表和连接时的版本显示与检查。{@code suppliedVersion} 是本端发给对方的版本，
     * {@code remoteVersionTest} 判断对方发来的版本（{@code null} 表示对方没有这个模组）是否可接受。
     */
    record DisplayTest(Supplier<String> suppliedVersion, BiPredicate<String, Boolean> remoteVersionTest)
            implements IExtensionPoint<DisplayTest> {
        public static final String IGNORESERVERONLY = "OHNOES😱😱😱😱"
                + "😱😱😱😱😱😱😱😱"
                + "😱😱😱😱😱";
        public static final Supplier<DisplayTest> IGNORE_SERVER_VERSION =
                () -> new DisplayTest(IGNORESERVERONLY, (remoteVersion, isFromServer) -> true);
        public static final Supplier<DisplayTest> IGNORE_ALL_VERSION =
                () -> new DisplayTest("", (remoteVersion, isFromServer) -> true);

        public DisplayTest(String version, BiPredicate<String, Boolean> remoteVersionTest) {
            this(() -> version, remoteVersionTest);
        }
    }
}
