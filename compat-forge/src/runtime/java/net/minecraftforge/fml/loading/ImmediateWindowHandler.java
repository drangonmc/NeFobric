package net.minecraftforge.fml.loading;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Forge 的“提前加载窗口”。NeFobric 没有这个窗口，窗口照原版创建，所以这里的方法都不做事；
 * 模组里常见的调用是 {@link #renderTick()} 和 {@link #updateProgress(String)}。
 */
public final class ImmediateWindowHandler {
    /** 和 mods.toml 里 openGLVersion 特性检查用的版本一致。 */
    public static final String GL_VERSION = "4.6";

    private ImmediateWindowHandler() {
    }

    public static void load(String launchTarget, String[] arguments) {
    }

    public static long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title,
                                            LongSupplier monitor) {
        return 0L;
    }

    public static boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter,
                                         IntConsumer xSetter, IntConsumer ySetter) {
        return false;
    }

    public static void updateFBSize(IntConsumer width, IntConsumer height) {
    }

    public static String getGLVersion() {
        return GL_VERSION;
    }

    public static <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex,
                                                 boolean fade) {
        return () -> null;
    }

    public static void acceptGameLayer(ModuleLayer layer) {
    }

    public static void renderTick() {
    }

    public static void updateProgress(String message) {
    }
}
