package dev.nefobric.compat.forge.devtools.remap;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 按继承关系重映射：字节码里成员引用的所有者常常是子类（{@code ServerLevel.isClientSide}），覆盖的方法声明在
 * 自己的类里，所以沿父类和接口一路找，第一个有映射的就是答案。lambda 的函数式接口方法名按接口里同名方法查。
 */
public final class HierarchyRemapper extends Remapper {
    private final Map<String, String> classes;
    private final MemberMap members;
    private final ClassIndex index;

    /** @param classes 类名表（内部名）；不在表里的类不改名 */
    public HierarchyRemapper(Map<String, String> classes, MemberMap members, ClassIndex index) {
        this.classes = classes;
        this.members = members;
        this.index = index;
    }

    @Override
    public String map(String internalName) {
        String mapped = classes.get(internalName);
        if (mapped != null) {
            return mapped;
        }
        // 映射表里没有、但外部类有的内部类（例如编译器生成的匿名类）跟着外部类改名
        int dollar = internalName.lastIndexOf('$');
        if (dollar > 0) {
            String outer = map(internalName.substring(0, dollar));
            if (!outer.equals(internalName.substring(0, dollar))) {
                return outer + internalName.substring(dollar);
            }
        }
        return internalName;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        for (String c : index.ancestors(owner)) {
            String r = members.field(c, name, descriptor);
            if (r != null) {
                return r;
            }
        }
        return name;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.startsWith("<") || owner.startsWith("[")) {
            return name;
        }
        for (String c : index.ancestors(owner)) {
            String r = members.method(c, name, descriptor);
            if (r != null) {
                return r;
            }
        }
        return name;
    }

    @Override
    public String mapRecordComponentName(String owner, String name, String descriptor) {
        return mapFieldName(owner, name, descriptor);
    }

    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        Type target = Type.getReturnType(descriptor);
        if (target.getSort() != Type.OBJECT) {
            return name;
        }
        for (String c : index.ancestors(target.getInternalName())) {
            Set<String> candidates = new HashSet<>();
            for (Map.Entry<String, String> e : members.methodsOf(c).entrySet()) {
                if (e.getKey().startsWith(name + "(")) {
                    candidates.add(e.getValue());
                }
            }
            if (candidates.size() == 1) {
                return candidates.iterator().next();
            }
        }
        return name;
    }
}
