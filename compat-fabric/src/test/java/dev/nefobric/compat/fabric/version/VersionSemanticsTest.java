package dev.nefobric.compat.fabric.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 版本号和版本条件的语义必须与 Fabric Loader 完全一致，模组的依赖检查和版本比较才会得到同样的结果。 */
class VersionSemanticsTest {

    private static Version v(String s) throws VersionParsingException {
        return Version.parse(s);
    }

    private static boolean matches(String predicate, String version) throws VersionParsingException {
        return VersionPredicate.parse(predicate).test(v(version));
    }

    @Test
    void parsesSemanticAndStringVersions() throws Exception {
        SemanticVersion sv = SemanticVersion.parse("0.92.2+1.20.1");
        assertEquals(3, sv.getVersionComponentCount());
        assertEquals(92, sv.getVersionComponent(1));
        assertEquals(0, sv.getVersionComponent(5));
        assertEquals("1.20.1", sv.getBuildKey().orElseThrow());
        assertTrue(sv.getPrereleaseKey().isEmpty());
        assertEquals("0.92.2+1.20.1", sv.getFriendlyString());

        SemanticVersion pre = SemanticVersion.parse("1.0.0-beta.2+build.5");
        assertEquals("beta.2", pre.getPrereleaseKey().orElseThrow());
        assertEquals("build.5", pre.getBuildKey().orElseThrow());

        assertInstanceOf(StringVersion.class, v("v1.0"));
        assertInstanceOf(StringVersion.class, v("1.0_01"));
        assertThrows(VersionParsingException.class, () -> SemanticVersion.parse("v1.0"));
        assertThrows(VersionParsingException.class, () -> v(""));
    }

    @Test
    void comparesLikeFabric() throws Exception {
        assertEquals(0, v("1.20").compareTo(v("1.20.0")));
        assertEquals(v("1.20"), v("1.20.0")); // Fabric 的 equals 也把缺的段当 0
        assertTrue(v("1.20.1").compareTo(v("1.20")) > 0);
        assertTrue(v("1.0.0-alpha").compareTo(v("1.0.0")) < 0);
        assertTrue(v("1.0.0-alpha").compareTo(v("1.0.0-alpha.1")) < 0);
        assertTrue(v("1.0.0-alpha.1").compareTo(v("1.0.0-alpha.beta")) < 0);
        assertTrue(v("1.0.0-beta.2").compareTo(v("1.0.0-beta.11")) < 0);
        assertTrue(v("1.0.0-rc.1").compareTo(v("1.0.0")) < 0);
        assertEquals(0, v("1.0.0+a").compareTo(v("1.0.0+b")));
        assertNotEquals(v("1.0.0+a"), v("1.0.0+b"));
        assertEquals(v("1.0.0+a"), v("1.0.0+a"));
    }

    @Test
    void predicatesMatchLikeFabric() throws Exception {
        assertTrue(matches("*", "anything"));
        assertTrue(matches(">=0.14.21", "0.16.10"));
        assertFalse(matches(">=0.14.21", "0.14.20"));
        assertTrue(matches(">=0.14.21 <0.17", "0.16.10"));
        assertFalse(matches(">=0.14.21 <0.17", "0.17.0"));
        // ~ 要求主、次版本相同
        assertTrue(matches("~1.20.1", "1.20.4"));
        assertFalse(matches("~1.20.1", "1.20.0"));
        assertFalse(matches("~1.20.1", "1.21"));
        assertTrue(matches("~1.20", "1.20.1"));
        // ^ 要求主版本相同
        assertTrue(matches("^0.92.0", "0.95.1"));
        assertFalse(matches("^0.92.0", "1.0.0"));
        // 通配：1.20.x 等价于 ~1.20-，所以 1.20 的预发布版也算
        assertTrue(matches("1.20.x", "1.20.1"));
        assertTrue(matches("1.20.x", "1.20"));
        assertTrue(matches("1.20.x", "1.20-pre1"));
        assertFalse(matches("1.20.x", "1.21"));
        assertTrue(matches("1.x", "1.20.1"));
        assertFalse(matches("1.x", "2.0"));
        assertTrue(matches("1.20.1", "1.20.1"));
        assertFalse(matches("1.20.1", "1.20.2"));
        // 预发布下界：>=1.20.1- 包括 1.20.1 的所有预发布版
        assertTrue(matches(">=1.20.1-", "1.20.1-rc.1"));
        assertFalse(matches(">=1.20.1", "1.20.1-rc.1"));
        // 非语义版本只能用等号类运算符
        assertTrue(matches("=v1", "v1"));
        assertTrue(matches(">=v1", "v1"));
        assertThrows(VersionParsingException.class, () -> VersionPredicate.parse(">v1"));
        assertThrows(VersionParsingException.class, () -> VersionPredicate.parse(">=1.x"));
        // 1.x.2 不是合法语义版本，Fabric 把它当普通字符串，只和 “1.x.2” 相等
        assertTrue(matches("1.x.2", "1.x.2"));
        assertFalse(matches("1.x.2", "1.5.2"));
    }

    @Test
    void intervalsCombineLikeFabric() throws Exception {
        VersionInterval i = VersionPredicate.parse(">=0.14.21 <0.17").getInterval();
        assertEquals(v("0.14.21"), i.getMin());
        assertTrue(i.isMinInclusive());
        assertEquals(v("0.17"), i.getMax());
        assertFalse(i.isMaxInclusive());

        VersionInterval caret = VersionPredicate.parse("^1.2.3").getInterval();
        assertEquals("2-", caret.getMax().getFriendlyString());

        List<VersionInterval> or = VersionInterval.or(List.of(VersionPredicate.parse("<1").getInterval()),
                VersionPredicate.parse(">=2").getInterval());
        assertEquals(2, or.size());
        List<VersionInterval> not = VersionInterval.not(or);
        assertEquals(1, not.size());
        assertEquals(v("1"), not.get(0).getMin());
        assertTrue(not.get(0).isMinInclusive());
        assertEquals(v("2"), not.get(0).getMax());
        assertFalse(not.get(0).isMaxInclusive());

        assertNull(VersionInterval.and(VersionPredicate.parse("<1").getInterval(), VersionPredicate.parse(">=2").getInterval()));
        assertTrue(VersionInterval.not(VersionInterval.INFINITE).isEmpty());
    }
}
