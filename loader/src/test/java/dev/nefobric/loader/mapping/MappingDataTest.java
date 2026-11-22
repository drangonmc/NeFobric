package dev.nefobric.loader.mapping;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MappingDataTest {

    @Test
    void mapsClassesBetweenAllNamespaces() throws IOException {
        MappingData m = MappingTestData.load();
        assertEquals("net.minecraft.world.Thing", m.mapClass("official", "srg", "a"));
        assertEquals("net.minecraft.world.Thing", m.mapClass("intermediary", "srg", "net.minecraft.class_1"));
        assertEquals("net.minecraft.class_2", m.mapClass("srg", "intermediary", "net.minecraft.world.SubThing"));
        assertEquals("a", m.mapClass("intermediary", "official", "net.minecraft.class_1"));
        assertEquals("net.minecraft.world.Thing$Inner",
                m.mapClass("intermediary", "srg", "net.minecraft.class_1$class_4"));
        // 不是游戏类：原样返回
        assertEquals("java.lang.String", m.mapClass("intermediary", "srg", "java.lang.String"));
        assertFalse(m.isIdentity());
        assertEquals("srg", m.runtimeNamespace());
    }

    @Test
    void mapsMembers() throws IOException {
        MappingData m = MappingTestData.load();
        assertEquals("m_100_", m.mapMethod("intermediary", "srg", "net.minecraft.class_1", "method_10", "()V"));
        assertEquals("f_200_", m.mapField("intermediary", "srg", "net.minecraft.class_1", "field_20", "I"));
        assertEquals("m_100_", m.mapMethod("official", "srg", "a", "a", "()V"));
        assertEquals("m_103_", m.mapMethod("official", "srg", "c", "a", "(I)I"));
        assertEquals("method_13", m.mapMethod("srg", "intermediary", "net.minecraft.world.Ticker", "m_103_", "(I)I"));
        assertEquals("b", m.mapField("srg", "official", "net.minecraft.world.Thing", "f_200_", "I"));
        // intermediary 名全局唯一，不需要所属类
        assertEquals("m_102_", m.mapMethod("intermediary", "srg", "whatever.Owner", "method_12", "()V"));
        // 描述符换名
        assertEquals("(Lnet/minecraft/world/Thing;)V", m.mapDescInternal("intermediary", "srg",
                "(Lnet/minecraft/class_1;)V"));
        assertEquals(0, m.uniqueConflicts());
    }

    @Test
    void readsLegacyTsrg() throws IOException {
        MappingData.Builder b = MappingData.builder(MappingStore.NAMESPACES);
        MappingReaders.readTsrg(MappingTestData.reader("""
                a net/minecraft/world/Thing
                \tb f_200_
                \ta ()V m_100_
                """), b, Mappings.SRG, Mappings.SRG);
        MappingData m = b.build(Mappings.SRG);
        assertEquals("net.minecraft.world.Thing", m.mapClass("official", "srg", "a"));
        assertEquals("f_200_", m.mapField("official", "srg", "a", "b", null));
        assertEquals("m_100_", m.mapMethod("official", "srg", "a", "a", "()V"));
    }
}
