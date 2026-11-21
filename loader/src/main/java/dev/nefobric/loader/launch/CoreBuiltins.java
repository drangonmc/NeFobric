package dev.nefobric.loader.launch;

import dev.nefobric.loader.NeFobric;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;

import java.util.List;

/** 核心提供的虚拟模组：minecraft、java、nefobric。各兼容层再补充 fabricloader、forge 等。 */
final class CoreBuiltins {
    private CoreBuiltins() {
    }

    static List<ModCandidate> create(GameInstall game) {
        String mc = game.version() != null ? game.version() : NeFobric.TARGET_MINECRAFT;
        return List.of(
                ModCandidate.builder("minecraft", mc, Ecosystem.BUILTIN).name("Minecraft").build(),
                ModCandidate.builder("java", System.getProperty("java.specification.version", "17"),
                        Ecosystem.BUILTIN).name("Java").build(),
                ModCandidate.builder("nefobric", NeFobric.VERSION, Ecosystem.BUILTIN).name("NeFobric").build());
    }
}
