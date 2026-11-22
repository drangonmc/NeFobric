package dev.nefobric.compat.forge.neoforge;

import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.version.MavenVersionRange;
import dev.nefobric.loader.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeIdentityTest {

    private static VersionPredicate forge(String range) {
        return NeoForgeIdentity.widen("forge", Dependency.Kind.REQUIRED, "47.4.0", MavenVersionRange.parse(range));
    }

    private static VersionPredicate javafml(String range) {
        return NeoForgeIdentity.widen("javafml", Dependency.Kind.REQUIRED, "47", MavenVersionRange.parse(range));
    }

    @Test
    void forgeRangesWrittenAgainstNeoForgeNumbersAreAccepted() {
        assertTrue(forge("[47.1,47.2)").test("47.4.0"));
        assertTrue(forge("[47.1.106]").test("47.4.0"));
        assertTrue(forge("[47,)").test("47.4.0"));
        assertTrue(forge("[47.1.3,)").test("47.4.0"));
    }

    @Test
    void forgeRangesOutside1201StillFail() {
        assertFalse(forge("[48,)").test("47.4.0"));
        assertFalse(forge("[46,47)").test("47.4.0"));
        // 要求比模拟的 47.4.0 更新的 Forge：两个身份都不满足
        assertFalse(forge("[47.4.10,)").test("47.4.0"));
    }

    @Test
    void aliasesOnlyApplyToTheReportedVersion() {
        // 只有内置模组报告的版本才会换成别名；别的版本号照原区间判断
        assertFalse(forge("[47.1,47.2)").test("47.3.0"));
    }

    @Test
    void loaderVersionAcceptsAnyForgeFamilyLanguageVersion() {
        assertTrue(javafml("[47,)").test("47"));
        assertTrue(javafml("[47.1,)").test("47"));
        assertTrue(javafml("[47.2,)").test("47"));
        assertTrue(javafml("[47.4,)").test("47"));
        assertTrue(javafml("[46,)").test("47"));
        assertFalse(javafml("[48,)").test("47"));
        assertFalse(javafml("[1,4)").test("47"));
    }

    @Test
    void incompatibleDependenciesAreNotWidened() {
        VersionPredicate p = MavenVersionRange.parse("[47.1,47.2)");
        assertSame(p, NeoForgeIdentity.widen("forge", Dependency.Kind.INCOMPATIBLE, "47.4.0", p));
        assertSame(p, NeoForgeIdentity.widen("forge", Dependency.Kind.DISCOURAGED, "47.4.0", p));
    }

    @Test
    void otherTargetsAreUntouched() {
        VersionPredicate p = MavenVersionRange.parse("[1.0,)");
        assertSame(p, NeoForgeIdentity.widen("jei", Dependency.Kind.REQUIRED, null, p));
        assertSame(p, NeoForgeIdentity.widen("minecraft", Dependency.Kind.REQUIRED, "1.20.1", p));
    }

    @Test
    void describeKeepsTheOriginalRange() {
        assertEquals("[47.1,47.2)", forge("[47.1,47.2)").describe());
    }

    @Test
    void recognisesNeoForgeRangesForMinecraft1202AndNewer() {
        assertEquals(Optional.of("[20.4,)"), NeoForgeIdentity.modernNeoForgeRange("[20.4,)"));
        assertEquals(Optional.of("[20.2.86,)"), NeoForgeIdentity.modernNeoForgeRange(" [20.2.86,) "));
        assertEquals(Optional.of("[21.1.0,21.2)"), NeoForgeIdentity.modernNeoForgeRange("[21.1.0,21.2)"));
        assertEquals(Optional.of("[26.1.0.5-beta,)"), NeoForgeIdentity.modernNeoForgeRange("[26.1.0.5-beta,)"));
        assertEquals(Optional.of("[20.4,20.5),[21,)"), NeoForgeIdentity.modernNeoForgeRange("[20.4,20.5),[21,)"));
        assertEquals(Optional.of("[20.6.119]"), NeoForgeIdentity.modernNeoForgeRange("[20.6.119]"));
        assertEquals(Optional.of("20.4"), NeoForgeIdentity.modernNeoForgeRange("20.4"));
    }

    @Test
    void ranges1201ModsCanUseAreNotFlagged() {
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange("[47.1,)"));
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange("[47,48)"));
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange("*"));
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange(""));
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange(null));
        // 没有下限的区间交给正常的版本检查
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange("(,21)"));
        // 只要有一段可能是 1.20.1 的编号就不算
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange("[20.4,21),[47.1,)"));
        assertEquals(Optional.empty(), NeoForgeIdentity.modernNeoForgeRange("[1,)"));
    }
}
