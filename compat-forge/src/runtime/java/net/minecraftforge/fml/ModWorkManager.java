package net.minecraftforge.fml;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;

public class ModWorkManager {
    public interface DrivenExecutor extends Executor {
        boolean selfDriven();

        boolean driveOne();

        default void drive(Runnable ticker) {
            if (!selfDriven()) {
                ticker.run();
                while (driveOne()) {
                    ticker.run();
                }
            } else {
                LockSupport.parkNanos(50_000_000L);
            }
        }
    }

    private static final class SyncExecutor implements DrivenExecutor {
        private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();

        @Override
        public boolean driveOne() {
            Runnable task = tasks.pollFirst();
            if (task != null) {
                task.run();
            }
            return task != null;
        }

        @Override
        public boolean selfDriven() {
            return false;
        }

        @Override
        public void execute(final Runnable command) {
            tasks.addLast(command);
        }
    }

    private static final class WrappingExecutor implements DrivenExecutor {
        private final Executor wrapped;

        WrappingExecutor(final Executor executor) {
            this.wrapped = executor;
        }

        @Override
        public boolean selfDriven() {
            return true;
        }

        @Override
        public boolean driveOne() {
            return false;
        }

        @Override
        public void execute(final Runnable command) {
            wrapped.execute(command);
        }
    }

    private static final SyncExecutor SYNC = new SyncExecutor();

    public static DrivenExecutor syncExecutor() {
        return SYNC;
    }

    public static DrivenExecutor wrappedExecutor(Executor executor) {
        return new WrappingExecutor(executor);
    }

    public static Executor parallelExecutor() {
        return ForkJoinPool.commonPool();
    }
}
