package dev.nefobric.compat.fabric.entrypoint;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import java.util.ArrayList;
import java.util.List;

/** 测试用的入口类。 */
public final class Entrypoints {
    static final List<String> CALLS = new ArrayList<>();

    private Entrypoints() {
    }

    public static class Both implements ModInitializer, ClientModInitializer {
        static int constructed;

        public Both() {
            constructed++;
        }

        @Override
        public void onInitialize() {
            CALLS.add("both.main");
        }

        @Override
        public void onInitializeClient() {
            CALLS.add("both.client");
        }
    }

    public static class Fails implements ModInitializer {
        @Override
        public void onInitialize() {
            throw new IllegalStateException("boom");
        }
    }

    public static class Holder {
        public static final ModInitializer FIELD = () -> CALLS.add("field");
        public static ModInitializer notFinal = () -> CALLS.add("notFinal");
        public final ModInitializer instanceField = () -> CALLS.add("instance");

        public static void staticMethod() {
            CALLS.add("staticMethod");
        }

        public void instanceMethod() {
            CALLS.add("instanceMethod");
        }

        public void overloaded() {
        }

        public void overloaded(int x) {
        }
    }
}
