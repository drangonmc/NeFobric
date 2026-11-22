package dev.nefobric.forge.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeConfigSpecTest {
    enum Mode { FAST, SLOW }

    @Test
    void defaultsBeforeLoadAndCorrection() {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("General settings").push("general");
        ForgeConfigSpec.BooleanValue enabled = b.comment("Turn it on").define("enabled", true);
        ForgeConfigSpec.IntValue count = b.defineInRange("count", 5, 1, 10);
        ForgeConfigSpec.DoubleValue scale = b.defineInRange("scale", 0.5, 0.0, 1.0);
        ForgeConfigSpec.EnumValue<Mode> mode = b.defineEnum("mode", Mode.FAST);
        ForgeConfigSpec.ConfigValue<List<? extends String>> names =
                b.defineList("names", List.of("a", "b"), o -> o instanceof String);
        b.pop();
        ForgeConfigSpec spec = b.build();

        // 配置文件加载前返回默认值（Forge 生产环境的行为）
        assertEquals(5, count.get());
        assertFalse(spec.isLoaded());

        CommentedConfig data = CommentedConfig.inMemory();
        data.set(List.of("general", "count"), 99);
        data.set(List.of("general", "mode"), "slow");
        data.set(List.of("general", "names"), List.of("x", 3, "y"));
        data.set(List.of("general", "unknown"), "drop me");
        assertFalse(spec.isCorrect(data));
        spec.acceptConfig(data);

        assertTrue(spec.isLoaded());
        assertTrue(enabled.get());
        assertEquals(10, count.get(), "out-of-range values are clamped");
        assertEquals(0.5, scale.get());
        assertEquals(Mode.SLOW, mode.get(), "enum names are case-insensitive");
        assertEquals(List.of("x", "y"), names.get(), "invalid list elements are removed");
        assertFalse(data.contains(List.of("general", "unknown")));
        assertEquals("General settings", data.<CommentedConfig>get("general") == null ? null
                : data.getComment("general"));
        assertEquals("Turn it on", ((CommentedConfig) data.get("general")).getComment("enabled"));
        assertEquals("Range: 1 ~ 10", ((CommentedConfig) data.get("general")).getComment("count"));
        assertTrue(spec.isCorrect(data));

        count.set(7);
        assertEquals(7, count.get());
        assertEquals(7, (int) data.get(List.of("general", "count")));
    }

    @Test
    void builderStateIsValidated() {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("dangling");
        assertThrows(IllegalStateException.class, b::build);
        assertThrows(IllegalArgumentException.class,
                () -> new ForgeConfigSpec.Builder().defineInRange("x", 1, 5, 2));
    }
}
