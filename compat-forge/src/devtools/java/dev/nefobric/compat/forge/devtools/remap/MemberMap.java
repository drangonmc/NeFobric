package dev.nefobric.compat.forge.devtools.remap;

import java.util.Map;

/** 一个类自己声明的成员的新名字。找不到时返回 {@code null}。类名、描述符都在来源命名空间里。 */
public interface MemberMap {
    String field(String owner, String name, String desc);

    String method(String owner, String name, String desc);

    /** 这个类有映射的全部方法：键是名字加描述符，值是新名字。用于 lambda 的函数式接口方法（只知道名字）。 */
    Map<String, String> methodsOf(String owner);
}
