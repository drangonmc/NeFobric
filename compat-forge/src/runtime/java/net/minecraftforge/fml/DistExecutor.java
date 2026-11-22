package net.minecraftforge.fml;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** 只在某一端运行代码。双层 Supplier 让另一端的类不会被加载。 */
public final class DistExecutor {
    private DistExecutor() {
    }

    @Deprecated
    public static <T> T callWhenOn(Dist dist, Supplier<Callable<T>> toRun) {
        return unsafeCallWhenOn(dist, toRun);
    }

    public static <T> T unsafeCallWhenOn(Dist dist, Supplier<Callable<T>> toRun) {
        if (dist != FMLEnvironment.dist) {
            return null;
        }
        try {
            return toRun.get().call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T safeCallWhenOn(Dist dist, Supplier<SafeCallable<T>> toRun) {
        return callWhenOn(dist, toRun::get);
    }

    @Deprecated
    public static void runWhenOn(Dist dist, Supplier<Runnable> toRun) {
        unsafeRunWhenOn(dist, toRun);
    }

    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {
        if (dist == FMLEnvironment.dist) {
            toRun.get().run();
        }
    }

    public static void safeRunWhenOn(Dist dist, Supplier<SafeRunnable> toRun) {
        if (dist == FMLEnvironment.dist) {
            toRun.get().run();
        }
    }

    @Deprecated
    public static <T> T runForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        return unsafeRunForDist(clientTarget, serverTarget);
    }

    public static <T> T unsafeRunForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        return switch (FMLEnvironment.dist) {
            case CLIENT -> clientTarget.get().get();
            case DEDICATED_SERVER -> serverTarget.get().get();
        };
    }

    public static <T> T safeRunForDist(Supplier<SafeSupplier<T>> clientTarget, Supplier<SafeSupplier<T>> serverTarget) {
        return switch (FMLEnvironment.dist) {
            case CLIENT -> clientTarget.get().get();
            case DEDICATED_SERVER -> serverTarget.get().get();
        };
    }

    public interface SafeReferent {
    }

    public interface SafeCallable<T> extends SafeReferent, Callable<T>, Serializable {
    }

    public interface SafeSupplier<T> extends SafeReferent, Supplier<T>, Serializable {
    }

    public interface SafeRunnable extends SafeReferent, Runnable, Serializable {
    }
}
