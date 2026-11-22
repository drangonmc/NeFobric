package net.minecraftforge.fml;

import java.util.function.Supplier;

/** 往崩溃报告和系统报告里加一段。 */
public interface ISystemReportExtender extends Supplier<String> {
    String getLabel();

    default boolean isActive() {
        return true;
    }
}
