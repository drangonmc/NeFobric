package net.minecraftforge.fml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class CrashReportCallables {
    private static final List<ISystemReportExtender> crashCallables = Collections.synchronizedList(new ArrayList<>());

    public static void registerCrashCallable(ISystemReportExtender callable) {
        crashCallables.add(callable);
    }

    public static void registerCrashCallable(String headerName, Supplier<String> reportGenerator) {
        registerCrashCallable(headerName, reportGenerator, () -> true);
    }

    public static void registerCrashCallable(String headerName, Supplier<String> reportGenerator, BooleanSupplier active) {
        registerCrashCallable(new ISystemReportExtender() {
            @Override
            public String getLabel() {
                return headerName;
            }

            @Override
            public String get() {
                return reportGenerator.get();
            }

            @Override
            public boolean isActive() {
                return active.getAsBoolean();
            }
        });
    }

    public static List<ISystemReportExtender> allCrashCallables() {
        synchronized (crashCallables) {
            return List.copyOf(crashCallables);
        }
    }
}
