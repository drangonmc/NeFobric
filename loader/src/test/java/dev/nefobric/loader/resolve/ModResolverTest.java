package dev.nefobric.loader.resolve;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.SideRestriction;
import dev.nefobric.loader.version.FabricVersionPredicate;
import dev.nefobric.loader.version.MavenVersionRange;
import dev.nefobric.loader.version.VersionPredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModResolverTest {

    @BeforeAll
    static void english() {
        System.setProperty("nefobric.lang", "en");
    }

    private static final List<ModCandidate> BUILTINS = List.of(
            ModCandidate.builder("minecraft", "1.20.1", Ecosystem.BUILTIN).build(),
            ModCandidate.builder("forge", "47.4.0", Ecosystem.BUILTIN).build(),
            ModCandidate.builder("fabricloader", "0.16.10", Ecosystem.BUILTIN).build());

    private static ModCandidate.Builder mod(String id, Ecosystem eco) {
        return ModCandidate.builder(id, "1.0.0", eco).origin(Paths.get(id + "-" + eco.name().toLowerCase() + ".jar"));
    }

    private static Dependency requires(String id, VersionPredicate p) {
        return Dependency.of(id, Dependency.Kind.REQUIRED, p);
    }

    private static ModResolver.Result resolve(Env env, ModCandidate... mods) {
        return new ModResolver(env).resolve(List.of(mods), BUILTINS);
    }

    @Test
    void satisfiedDependenciesLoadDependencyFirst() {
        ModResolver.Result r = resolve(Env.CLIENT,
                mod("a_mod", Ecosystem.FABRIC).dependency(requires("z_lib", VersionPredicate.any()))
                        .dependency(requires("minecraft", FabricVersionPredicate.parse("~1.20.1"))).build(),
                mod("z_lib", Ecosystem.FORGE).dependency(requires("forge", MavenVersionRange.parse("[47,)"))).build());
        assertFalse(r.hasErrors(), r.errors().toString());
        assertEquals(List.of("z_lib", "a_mod"), r.mods().stream().map(ModCandidate::id).toList());
    }

    @Test
    void missingAndWrongVersionAreErrors() {
        ModResolver.Result r = resolve(Env.CLIENT,
                mod("needs_api", Ecosystem.FABRIC).dependency(requires("fabric-api", VersionPredicate.any())).build(),
                mod("old_mod", Ecosystem.FORGE).dependency(requires("minecraft", MavenVersionRange.parse("[1.19.2,1.19.3)")))
                        .build());
        assertEquals(2, r.errors().size(), r.errors().toString());
        assertTrue(r.errors().get(0).contains("requires fabric-api"), r.errors().get(0));
        assertTrue(r.errors().get(1).contains("but version 1.20.1 is installed"), r.errors().get(1));
    }

    @Test
    void sameIdInBothEcosystemsPrefersFabric() {
        ModCandidate fabric = mod("jei", Ecosystem.FABRIC).build();
        ModCandidate forge = mod("jei", Ecosystem.FORGE).build();
        ModResolver.Result r = resolve(Env.CLIENT, forge, fabric);
        assertFalse(r.hasErrors());
        assertEquals(List.of(fabric), r.mods());
        assertEquals(1, r.warnings().size());
    }

    @Test
    void sameIdTwiceInOneEcosystemIsAnError() {
        ModResolver.Result r = resolve(Env.CLIENT, mod("dup", Ecosystem.FABRIC).build(),
                ModCandidate.builder("dup", "2.0.0", Ecosystem.FABRIC).origin(Paths.get("dup-2.jar")).build());
        assertEquals(1, r.errors().size());
        assertTrue(r.errors().get(0).contains("dup-2.jar"));
    }

    @Test
    void reservedIdsAreRejected() {
        ModResolver.Result r = resolve(Env.CLIENT, mod("minecraft", Ecosystem.FABRIC).build());
        assertEquals(1, r.errors().size());
    }

    @Test
    void environmentFilterAndSideSpecificDependencies() {
        ModCandidate clientOnly = mod("hud", Ecosystem.FABRIC).environment(SideRestriction.CLIENT).build();
        ModCandidate withClientDep = mod("thing", Ecosystem.FORGE)
                .dependency(new Dependency("not_installed", Dependency.Kind.REQUIRED, VersionPredicate.any(),
                        SideRestriction.CLIENT, Dependency.Ordering.NONE))
                .build();
        ModResolver.Result server = resolve(Env.SERVER, clientOnly, withClientDep);
        assertFalse(server.hasErrors(), server.errors().toString());
        assertEquals(List.of("thing"), server.mods().stream().map(ModCandidate::id).toList());
        ModResolver.Result client = resolve(Env.CLIENT, clientOnly, withClientDep);
        assertEquals(1, client.errors().size());
    }

    @Test
    void breaksAndProvides() {
        ModResolver.Result r = resolve(Env.CLIENT,
                mod("a", Ecosystem.FABRIC).dependency(Dependency.of("b", Dependency.Kind.INCOMPATIBLE,
                        VersionPredicate.any())).build(),
                mod("b_real", Ecosystem.FABRIC).provides("b").build());
        assertEquals(1, r.errors().size());
        assertTrue(r.byId().containsKey("b"));
    }

    @Test
    void orderingBeforeAndCycles() {
        ModResolver.Result r = resolve(Env.CLIENT,
                mod("a", Ecosystem.FORGE).dependency(new Dependency("b", Dependency.Kind.OPTIONAL,
                        VersionPredicate.any(), SideRestriction.ANY, Dependency.Ordering.AFTER)).build(),
                mod("b", Ecosystem.FORGE).build(),
                mod("c", Ecosystem.FORGE).dependency(new Dependency("d", Dependency.Kind.OPTIONAL,
                        VersionPredicate.any(), SideRestriction.ANY, Dependency.Ordering.BEFORE)).build(),
                mod("d", Ecosystem.FORGE).build(),
                mod("x", Ecosystem.FABRIC).dependency(requires("y", VersionPredicate.any())).build(),
                mod("y", Ecosystem.FABRIC).dependency(requires("x", VersionPredicate.any())).build());
        List<String> order = r.mods().stream().map(ModCandidate::id).toList();
        assertTrue(order.indexOf("b") < order.indexOf("a"), order.toString());
        assertTrue(order.indexOf("c") < order.indexOf("d"), order.toString());
        assertEquals(6, order.size());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("Circular")), r.warnings().toString());
    }

    // ---- Jar-in-Jar ----

    private static ModCandidate nested(String id, String version, ModCandidate parent) {
        ModCandidate c = ModCandidate.builder(id, version, Ecosystem.FABRIC)
                .origin(Paths.get("cache", id + "-" + version + ".jar")).build();
        c.linkNested(parent, "META-INF/jars/" + id + "-" + version + ".jar");
        return c;
    }

    private static List<String> idsAndVersions(ModResolver.Result r) {
        return r.mods().stream().map(m -> m.id() + "@" + m.version()).sorted().toList();
    }

    @Test
    void nestedDuplicatesKeepTheNewestVersion() {
        ModCandidate a = mod("a", Ecosystem.FABRIC).build();
        ModCandidate b = mod("b", Ecosystem.FABRIC).build();
        ModResolver.Result r = resolve(Env.CLIENT, a, b,
                nested("fabric-api-base", "0.4.29+b04edc7a77", a),
                nested("fabric-api-base", "0.4.31+ef105b4977", b),
                nested("fabric-api-base", "0.4.31+ef105b4977", a));
        assertFalse(r.hasErrors(), r.errors().toString());
        assertEquals(List.of("a@1.0.0", "b@1.0.0", "fabric-api-base@0.4.31+ef105b4977"), idsAndVersions(r));
        assertEquals(1, r.notes().size(), r.notes().toString());
        assertTrue(r.notes().get(0).contains("0.4.29"), r.notes().get(0));
    }

    @Test
    void sameVersionTopLevelCopyWinsOverNestedOneAndNewerNestedWinsOverTopLevel() {
        ModCandidate parent = mod("parent", Ecosystem.FABRIC).build();
        ModCandidate topLib = ModCandidate.builder("lib", "2.0.0", Ecosystem.FABRIC).origin(Paths.get("lib.jar")).build();
        ModResolver.Result same = resolve(Env.CLIENT, parent, nested("lib", "2.0.0", parent), topLib);
        assertFalse(same.hasErrors(), same.errors().toString());
        assertTrue(same.mods().contains(topLib));

        ModCandidate parent2 = mod("parent", Ecosystem.FABRIC).build();
        ModCandidate newer = nested("lib", "2.1.0", parent2);
        ModResolver.Result r = resolve(Env.CLIENT, parent2, newer,
                ModCandidate.builder("lib", "2.0.0", Ecosystem.FABRIC).origin(Paths.get("lib.jar")).build());
        assertFalse(r.hasErrors(), r.errors().toString());
        assertTrue(r.mods().contains(newer));
    }

    @Test
    void forgeStyleVersionsAreComparedToo() {
        ModCandidate a = mod("a", Ecosystem.FORGE).build();
        ModCandidate b = mod("b", Ecosystem.FORGE).build();
        ModResolver.Result r = resolve(Env.CLIENT, a, b, nested("geckolib", "4.4.10", a), nested("geckolib", "4.4.9", b));
        assertEquals(List.of("a@1.0.0", "b@1.0.0", "geckolib@4.4.10"), idsAndVersions(r));
        assertTrue(ModResolver.compareVersions("1.20.1-47.1.3", "1.20.1-47.1.10") < 0);
        assertTrue(ModResolver.compareVersions("0.92.2+1.20.1", "0.92.12+1.20.1") < 0);
    }

    @Test
    void nestedModsOfAnUnselectedCopyAreNotLoaded() {
        // 两个模组内嵌了不同版本的 lib，旧版 lib 里又内嵌了 old_only；选了新版 lib 之后 old_only 不应该加载
        ModCandidate a = mod("a", Ecosystem.FABRIC).build();
        ModCandidate b = mod("b", Ecosystem.FABRIC).build();
        ModCandidate oldLib = nested("lib", "1.0.0", a);
        ModCandidate newLib = nested("lib", "2.0.0", b);
        ModCandidate oldOnly = nested("old_only", "1.0.0", oldLib);
        ModCandidate shared1 = nested("shared", "1.0.0", oldLib);
        ModCandidate shared2 = nested("shared", "1.0.0", newLib);
        ModResolver.Result r = resolve(Env.CLIENT, a, b, oldLib, newLib, oldOnly, shared1, shared2);
        assertFalse(r.hasErrors(), r.errors().toString());
        assertEquals(List.of("a@1.0.0", "b@1.0.0", "lib@2.0.0", "shared@1.0.0"), idsAndVersions(r));
        assertTrue(r.mods().contains(shared2), "the copy inside the selected lib is used");
    }

    @Test
    void nestedModsFollowTheirParentsSide() {
        ModCandidate clientOnly = mod("hud", Ecosystem.FABRIC).environment(SideRestriction.CLIENT).build();
        ModCandidate lib = nested("hud_lib", "1.0.0", clientOnly);
        ModCandidate libOfLib = nested("hud_lib_core", "1.0.0", lib);
        ModResolver.Result server = resolve(Env.SERVER, clientOnly, lib, libOfLib);
        assertFalse(server.hasErrors(), server.errors().toString());
        assertTrue(server.mods().isEmpty(), server.mods().toString());
        assertEquals(3, server.notes().size(), server.notes().toString());
        ModResolver.Result client = resolve(Env.CLIENT, clientOnly, lib, libOfLib);
        assertEquals(3, client.mods().size());
    }

    @Test
    void nestedCopiesOfBuiltinsAndMixinExtrasAreDropped() {
        ModCandidate a = mod("a", Ecosystem.FABRIC).build();
        ModResolver.Result r = new ModResolver(Env.CLIENT).resolve(List.of(a,
                        nested("fabricloader", "0.14.0", a),
                        nested("mixinextras", "0.3.5", a),
                        ModCandidate.builder("mixinextras", "0.2.0", Ecosystem.FORGE).origin(Paths.get("mixinextras-forge.jar"))
                                .build()),
                List.of(BUILTINS.get(0), BUILTINS.get(2),
                        ModCandidate.builder("mixinextras", "0.4.1", Ecosystem.BUILTIN).build()));
        assertFalse(r.hasErrors(), r.errors().toString());
        assertEquals(List.of(a), r.mods());
        assertEquals(2, r.notes().size(), r.notes().toString());
        assertTrue(r.notes().get(1).contains("mixinextras-forge.jar"), r.notes().get(1));

        // 直接放进 mods 的同名模组仍然报错
        ModResolver.Result direct = resolve(Env.CLIENT, mod("fabricloader", Ecosystem.FABRIC).build());
        assertEquals(1, direct.errors().size());
    }
}
