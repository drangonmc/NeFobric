package net.minecraftforge.forgespi.language;

import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** 一个模组 jar 的扫描结果：所有类的继承关系和所有注解。模组常用它找带自定义注解的类。 */
public class ModFileScanData {
    private final Set<AnnotationData> annotations = new LinkedHashSet<>();
    private final Set<ClassData> classes = new LinkedHashSet<>();
    private final List<IModFileInfo> modFiles = new ArrayList<>();
    private Map<String, ? extends IModLanguageProvider.IModLanguageLoader> targets = Collections.emptyMap();

    public static Predicate<Type> interestingAnnotations() {
        return t -> true;
    }

    public Set<ClassData> getClasses() {
        return classes;
    }

    public Set<AnnotationData> getAnnotations() {
        return annotations;
    }

    public void addLanguageLoader(Map<String, ? extends IModLanguageProvider.IModLanguageLoader> modTargetMap) {
        targets = modTargetMap;
    }

    public void addModFileInfo(IModFileInfo info) {
        modFiles.add(info);
    }

    public Map<String, ? extends IModLanguageProvider.IModLanguageLoader> getTargets() {
        return targets;
    }

    public List<IModFileInfo> getIModInfoData() {
        return modFiles;
    }

    public record ClassData(Type clazz, Type parent, Set<Type> interfaces) {
    }

    public record AnnotationData(Type annotationType, ElementType targetType, Type clazz, String memberName,
                                 Map<String, Object> annotationData) {
    }
}
