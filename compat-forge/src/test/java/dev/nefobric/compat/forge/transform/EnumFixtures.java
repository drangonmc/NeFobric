package dev.nefobric.compat.forge.transform;

import net.minecraftforge.common.IExtensibleEnum;

/** {@link EnumExtenderTest} 用的枚举（public：测试从另一个类加载器加载它们后要反射调用）。 */
public final class EnumFixtures {
    private EnumFixtures() {
    }

    /** 像模组自己写的可扩展枚举：两个 create 对应两个构造器，其中一个带 long/double（占两个槽）。 */
    public enum Extensible implements IExtensibleEnum {
        ALPHA(1, "a"),
        BETA(2L, 0.5d);

        static int inits;

        final int number;
        final String text;
        final long big;
        final double ratio;

        Extensible(int number, String text) {
            this.number = number;
            this.text = text;
            this.big = 0;
            this.ratio = 0;
        }

        Extensible(long big, double ratio) {
            this.number = 0;
            this.text = null;
            this.big = big;
            this.ratio = ratio;
        }

        public static Extensible create(String name, int number, String text) {
            throw new IllegalStateException("Enum not extended");
        }

        public static Extensible create(String name, long big, double ratio) {
            throw new IllegalStateException("Enum not extended");
        }

        @Override
        @SuppressWarnings("deprecation")
        public void init() {
            inits++;
        }
    }

    /** 像原版的 BlockPathTypes：没有实现接口，也没有 create，由 GAME_PATCH 加上。 */
    public enum VanillaLike {
        ONE(1.5f),
        TWO(2.5f);

        final float malus;

        VanillaLike(float malus) {
            this.malus = malus;
        }
    }

    /** create 的参数对不上任何构造器。 */
    public enum NoMatchingConstructor implements IExtensibleEnum {
        X;

        public static NoMatchingConstructor create(String name, int nope) {
            throw new IllegalStateException("Enum not extended");
        }
    }

    /** 带常量体的抽象枚举：GAME_PATCH 不动它。 */
    public enum WithBodies {
        A {
            @Override
            int value() {
                return 1;
            }
        };

        abstract int value();
    }
}
