package net.minecraftforge.eventbus.api;

import net.minecraftforge.eventbus.ListenerList;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/**
 * 所有事件的基类。是否可取消、是否带结果由类上的 {@link Cancelable}、{@link HasResult} 注解决定（会被子类继承）。
 */
public class Event {
    /** 标记带结果（{@link Result}）的事件。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface HasResult {
    }

    public enum Result {
        DENY,
        DEFAULT,
        ALLOW;

        public boolean isDefault() {
            return this == DEFAULT;
        }

        public boolean isDenied() {
            return this == DENY;
        }

        public boolean isAllowed() {
            return this == ALLOW;
        }
    }

    private boolean canceled;
    private Result result = Result.DEFAULT;
    private EventPriority phase;

    public Event() {
    }

    public boolean isCancelable() {
        return EventListenerHelper.isCancelable(getClass());
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean cancel) {
        if (!isCancelable()) {
            throw new UnsupportedOperationException(
                    "Attempted to call Event#setCanceled() on a non-cancelable event of type: "
                            + getClass().getCanonicalName());
        }
        if (phase == EventPriority.MONITOR) {
            throw new IllegalStateException("Attempted to call Event#setCanceled() after the MONITOR phase");
        }
        canceled = cancel;
    }

    public void cancel() {
        setCanceled(true);
    }

    public boolean hasResult() {
        return EventListenerHelper.hasResult(getClass());
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result value) {
        result = value;
    }

    public ListenerList getListenerList() {
        return EventListenerHelper.listFor(getClass());
    }

    public EventPriority getPhase() {
        return phase;
    }

    public void setPhase(EventPriority value) {
        Objects.requireNonNull(value, "setPhase argument must not be null");
        int previous = phase == null ? -1 : phase.ordinal();
        if (previous >= value.ordinal()) {
            throw new IllegalArgumentException("Attempted to set event phase to " + value + " when already " + phase);
        }
        phase = value;
    }
}
