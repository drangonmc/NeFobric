package dev.nefobric.loader.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * 一套很小但结构和真实文件一样的映射：
 * <pre>
 *   官方名   Mojang 名                         intermediary              SRG 成员
 *   a        net/minecraft/world/Thing         net/minecraft/class_1
 *     a()V   (tick)                            method_10                 m_100_
 *     b  I   (count)                           field_20                  f_200_
 *     c()V   (privateHelper，私有)              method_11                 m_101_
 *   b        net/minecraft/world/SubThing      net/minecraft/class_2
 *     a()V   (tick，重写)                       method_10                 m_100_
 *     c()V   (另一个与父类私有方法同名的方法)    method_12                 m_102_
 *   c        net/minecraft/world/Ticker        net/minecraft/class_3     （接口）
 *     a(I)I  (apply)                           method_13                 m_103_
 *   a$a      net/minecraft/world/Thing$Inner   net/minecraft/class_1$class_4
 * </pre>
 */
final class MappingTestData {
    static final String PROGUARD = """
            # comment
            net.minecraft.world.Thing -> a:
                int count -> b
                void tick() -> a
                void privateHelper() -> c
            net.minecraft.world.SubThing -> b:
                void tick() -> a
                void other() -> c
            net.minecraft.world.Ticker -> c:
                int apply(int) -> a
            net.minecraft.world.Thing$Inner -> a$a:
            """;

    static final String TSRG2 = """
            tsrg2 obf srg id
            a net/minecraft/world/Thing 1
            \tb f_200_ 200
            \ta ()V m_100_ 100
            \tc ()V m_101_ 101
            \t\tstatic
            b net/minecraft/world/SubThing 2
            \ta ()V m_100_ 100
            \tc ()V m_102_ 102
            c net/minecraft/world/Ticker 3
            \ta (I)I m_103_ 103
            \t\t0 o p_103_0_ 1030
            a$a net/minecraft/world/Thing$Inner 4
            """;

    static final String TINY2 = """
            tiny\t2\t0\tofficial\tintermediary
            c\ta\tnet/minecraft/class_1
            \tf\tI\tb\tfield_20
            \tm\t()V\ta\tmethod_10
            \tm\t()V\tc\tmethod_11
            \t\tc\tsome comment
            c\tb\tnet/minecraft/class_2
            \tm\t()V\ta\tmethod_10
            \tm\t()V\tc\tmethod_12
            c\tc\tnet/minecraft/class_3
            \tm\t(I)I\ta\tmethod_13
            \t\tp\t1\t\tvalue
            c\ta$a\tnet/minecraft/class_1$class_4
            """;

    private MappingTestData() {
    }

    static MappingData load() throws IOException {
        MappingData.Builder b = MappingData.builder(MappingStore.NAMESPACES);
        MappingReaders.readProguardClasses(reader(PROGUARD), b, Mappings.SRG);
        MappingReaders.readTsrg(reader(TSRG2), b, null, Mappings.SRG);
        MappingReaders.readTiny2(reader(TINY2), b, Mappings.INTERMEDIARY, Mappings.INTERMEDIARY);
        return b.build(Mappings.SRG);
    }

    static BufferedReader reader(String s) {
        return new BufferedReader(new StringReader(s));
    }
}
