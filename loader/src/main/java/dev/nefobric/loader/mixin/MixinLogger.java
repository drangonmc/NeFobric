package dev.nefobric.loader.mixin;

import dev.nefobric.loader.util.Log;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;

/** 把 Mixin 的日志写进 NeFobric 的日志（控制台和 nefobric-latest.log），来源标成 Mixin 的日志名。 */
final class MixinLogger extends LoggerAdapterAbstract {
    MixinLogger(String name) {
        super(name);
    }

    @Override
    public String getType() {
        return "NeFobric";
    }

    @Override
    public void catching(Level level, Throwable t) {
        log(level, "Catching " + t, t);
    }

    @Override
    public void log(Level level, String message, Object... params) {
        Log.Level ours = map(level);
        if (ours == null) {
            return;
        }
        FormattedMessage m = new FormattedMessage(message, params);
        Log.log(ours, getId(), m.getMessage(), m.hasThrowable() ? m.getThrowable() : null);
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        Log.Level ours = map(level);
        if (ours != null) {
            Log.log(ours, getId(), message, t);
        }
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        log(Level.WARN, "Throwing " + t, t);
        return t;
    }

    /** DEBUG、TRACE 只在 -Dnefobric.debug=true 时输出（Mixin 的 debug 日志非常多）。 */
    private static Log.Level map(Level level) {
        return switch (level) {
            case FATAL, ERROR -> Log.Level.ERROR;
            case WARN -> Log.Level.WARN;
            case INFO -> Log.Level.INFO;
            case DEBUG, TRACE -> Log.isDebug() ? Log.Level.DEBUG : null;
        };
    }
}
