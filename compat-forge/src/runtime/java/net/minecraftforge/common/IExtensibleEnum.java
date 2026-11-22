package net.minecraftforge.common;

/**
 * 实现了这个接口的枚举可以在运行时追加常量。
 *
 * <p>枚举里要有一个或多个静态方法 {@code create}：第一个参数是常量名（String），其余参数和某个构造器的参数一一对应，
 * 返回这个枚举类型。方法体写什么都行（通常是 {@code throw new IllegalStateException("Enum not extended")}），
 * 加载时由兼容层的 {@code EnumExtender} 改写成：同名（不分大小写）的常量已存在就直接返回它，否则用对应的构造器
 * 新建一个常量，序号排在最后，加进 {@code values()}，清掉 {@code valueOf}、{@code EnumSet} 用的缓存，调用
 * {@link #init()}，再返回它。</p>
 *
 * <p>Forge 里还有一个静态方法 {@code createCodecForExtensibleEnum(Supplier, Function)}，它引用原版的
 * {@code StringRepresentable}，等运行时能对着原版类编译（src/runtimeMc）以后再补。</p>
 */
public interface IExtensibleEnum {
    /**
     * 由改写后的 {@code create} 在新常量建好、加进 {@code values()} 以后调用，做构造器之后的初始化。
     * 不要手动调用。
     */
    @Deprecated
    default void init() {
    }
}
