package dev.nefobric.loader.version;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionPredicateTest {

    private static boolean fabric(String predicate, String version) {
        return FabricVersionPredicate.parse(predicate).test(version);
    }

    private static boolean maven(String range, String version) {
        return MavenVersionRange.parse(range).test(version);
    }

    @Test
    void semanticVersionOrdering() {
        assertTrue(SemanticVersion.parse("1.20.1", false).compareTo(SemanticVersion.parse("1.20", false)) > 0);
        assertEquals(0, SemanticVersion.parse("1.20", false).compareTo(SemanticVersion.parse("1.20.0", false)));
        assertTrue(SemanticVersion.parse("1.0.0-beta.2", false).compareTo(SemanticVersion.parse("1.0.0-beta.11", false)) < 0);
        assertTrue(SemanticVersion.parse("1.0.0-rc.1", false).compareTo(SemanticVersion.parse("1.0.0", false)) < 0);
        // 构建信息不参与比较
        assertEquals(0, SemanticVersion.parse("0.5.1+1.20.1", false).compareTo(SemanticVersion.parse("0.5.1", false)));
        assertEquals(null, SemanticVersion.tryParse("v1.2"));
    }

    @Test
    void fabricPredicates() {
        assertTrue(fabric("*", "anything"));
        assertTrue(fabric(">=0.14.21", "0.16.10"));
        assertFalse(fabric(">=0.15", "0.14.22"));
        assertTrue(fabric("~1.20.1", "1.20.4"));
        assertFalse(fabric("~1.20.1", "1.21"));
        assertFalse(fabric("~1.20.1", "1.20"));
        assertTrue(fabric("^1.2.3", "1.9.0"));
        assertFalse(fabric("^1.2.3", "2.0.0"));
        assertTrue(fabric("1.20.x", "1.20.1"));
        assertFalse(fabric("1.20.x", "1.21.0"));
        assertTrue(fabric(">=1.20 <1.21", "1.20.1"));
        assertFalse(fabric(">=1.20 <1.21", "1.21"));
        assertTrue(fabric("1.20.1", "1.20.1"));
        assertTrue(fabric("=1.0.0", "1.0.0+build.5"));
        assertTrue(fabric(">=17", "17"));
        assertTrue(fabric(">=17", "21"));
        // 非语义版本按字符串相等比较
        assertTrue(fabric("custom-build", "custom-build"));
        assertFalse(fabric(">=1.0", "custom-build"));
        assertThrows(IllegalArgumentException.class, () -> FabricVersionPredicate.parse(">=not-a-version!"));
    }

    @Test
    void fabricAnyOf() {
        VersionPredicate p = VersionPredicate.anyOf(List.of(FabricVersionPredicate.parse("1.20"),
                FabricVersionPredicate.parse("1.20.1")));
        assertTrue(p.test("1.20.1"));
        assertTrue(p.test("1.20"));
        assertFalse(p.test("1.20.2"));
        assertEquals("1.20 || 1.20.1", p.describe());
    }

    @Test
    void mavenVersionOrdering() {
        assertEquals(0, new MavenVersion("1.0").compareTo(new MavenVersion("1.0.0")));
        assertTrue(new MavenVersion("47.4.0").compareTo(new MavenVersion("47")) > 0);
        assertTrue(new MavenVersion("1.0-alpha").compareTo(new MavenVersion("1.0")) < 0);
        assertTrue(new MavenVersion("1.0-rc1").compareTo(new MavenVersion("1.0-beta2")) > 0);
        assertTrue(new MavenVersion("1.0.1").compareTo(new MavenVersion("1.0-rc1")) > 0);
        assertTrue(new MavenVersion("1.0-SNAPSHOT").compareTo(new MavenVersion("1.0")) < 0);
        assertTrue(new MavenVersion("1.0-sp1").compareTo(new MavenVersion("1.0")) > 0);
        assertTrue(new MavenVersion("1.20.10").compareTo(new MavenVersion("1.20.9")) > 0);
    }

    @Test
    void mavenRanges() {
        assertTrue(maven("[47,)", "47.4.0"));
        assertTrue(maven("[47,)", "47"));
        assertFalse(maven("[47,)", "46.0.14"));
        assertTrue(maven("[1.20.1,1.21)", "1.20.1"));
        assertFalse(maven("[1.20.1,1.21)", "1.21"));
        assertFalse(maven("(1.20.1,1.21)", "1.20.1"));
        assertTrue(maven("(,2.0]", "2.0"));
        assertTrue(maven("[1.0]", "1.0.0"));
        assertFalse(maven("[1.0]", "1.0.1"));
        assertTrue(maven("[1.0,2.0),[3.0,)", "3.1"));
        assertFalse(maven("[1.0,2.0),[3.0,)", "2.5"));
        // Forge 的软要求：不带括号的版本号和 * 都表示任意版本
        assertTrue(maven("1.0", "0.1"));
        assertTrue(maven("*", "0.1"));
        assertTrue(maven("", "0.1"));
        assertThrows(IllegalArgumentException.class, () -> MavenVersionRange.parse("[1.0"));
        assertThrows(IllegalArgumentException.class, () -> MavenVersionRange.parse("[2.0,1.0]"));
        assertThrows(IllegalArgumentException.class, () -> MavenVersionRange.parse("(1.0)"));
    }
}
