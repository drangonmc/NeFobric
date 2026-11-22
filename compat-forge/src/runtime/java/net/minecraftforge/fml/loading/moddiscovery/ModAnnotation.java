package net.minecraftforge.fml.loading.moddiscovery;

import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** 扫描时读到的一个注解。枚举值存成 {@link EnumHolder}，数组存成 {@link java.util.List}，嵌套注解存成 Map。 */
public class ModAnnotation {
    public static ModFileScanData.AnnotationData fromModAnnotation(final Type clazz, final ModAnnotation annotation) {
        return new ModFileScanData.AnnotationData(annotation.asmType, annotation.type, clazz, annotation.member,
                annotation.values);
    }

    public static class EnumHolder {
        private final String desc;
        private final String value;

        public EnumHolder(String desc, String value) {
            this.desc = desc;
            this.value = value;
        }

        public String getDesc() {
            return desc;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private final ElementType type;
    private final Type asmType;
    private final String member;
    private final Map<String, Object> values = new HashMap<>();
    private ArrayList<Object> arrayList;
    private String arrayName;

    public ModAnnotation(ElementType type, Type asmType, String member) {
        this.type = type;
        this.asmType = asmType;
        this.member = member;
    }

    public ModAnnotation(Type asmType, ModAnnotation parent) {
        this.type = parent.type;
        this.asmType = asmType;
        this.member = parent.member;
    }

    @Override
    public String toString() {
        return "Annotation{type=" + type + ", name=" + asmType.getClassName() + ", member=" + member
                + ", values=" + values + "}";
    }

    public ElementType getType() {
        return type;
    }

    public Type getASMType() {
        return asmType;
    }

    public String getMember() {
        return member;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void addArray(String name) {
        this.arrayList = new ArrayList<>();
        this.arrayName = name;
    }

    public void addProperty(String key, Object value) {
        if (this.arrayList != null) {
            arrayList.add(value);
        } else {
            values.put(key, value);
        }
    }

    public void addEnumProperty(String key, String enumName, String value) {
        addProperty(key, new EnumHolder(enumName, value));
    }

    public void endArray() {
        values.put(arrayName, arrayList);
        arrayList = null;
    }

    public ModAnnotation addChildAnnotation(String name, String desc) {
        ModAnnotation child = new ModAnnotation(Type.getType(desc), this);
        addProperty(name, child.getValues());
        return child;
    }
}
