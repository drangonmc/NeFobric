package net.minecraftforge.accesstransformer.parser;

import dev.nefobric.compat.forge.transform.AccessTransformers;
import net.minecraftforge.accesstransformer.AccessTransformer;
import net.minecraftforge.accesstransformer.ClassTarget;
import net.minecraftforge.accesstransformer.FieldTarget;
import net.minecraftforge.accesstransformer.INameHandler;
import net.minecraftforge.accesstransformer.MethodTarget;
import net.minecraftforge.accesstransformer.Target;
import net.minecraftforge.accesstransformer.TargetType;
import net.minecraftforge.accesstransformer.WildcardTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 合并后的访问变换表，按目标去重。文件格式的解析和核心那份（真正改字节码的）用的是同一个实现。
 *
 * <p>字段名和 Forge 的一致：有模组用反射换掉 {@code accessTransformers}（例如 ModernFix 换成带索引的 Map），
 * 所以这里每次都从这个字段读，不另存索引。</p>
 */
public class AccessTransformerList {
    private static final Logger LOGGER = LogManager.getLogger("AXFORM");
    private Map<Target<?>, AccessTransformer> accessTransformers = new HashMap<>();
    private INameHandler nameHandler = new IdentityNameHandler();

    public AccessTransformerList() {
    }

    public void loadFromResource(String resourceName) throws URISyntaxException, IOException {
        URL url = getClass().getClassLoader().getResource(resourceName);
        if (url == null) {
            throw new IOException("Access transformer resource not found: " + resourceName);
        }
        loadFromPath(Path.of(url.toURI()), resourceName);
    }

    public void loadFromPath(Path path, String resourceName) throws IOException {
        loadFromText(Files.readString(path, StandardCharsets.UTF_8), resourceName);
    }

    /** NeFobric：直接从原文读（启动时模组 jar 里的 AT 文件就是这样交过来的）。 */
    public void loadFromText(String text, String origin) {
        List<String> problems = new ArrayList<>();
        for (AccessTransformers.Entry e : AccessTransformers.parseEntries(text, origin, problems)) {
            add(toTransformer(e, origin));
        }
        problems.forEach(p -> LOGGER.warn("Invalid access transformer line, skipped: {}", p));
    }

    private AccessTransformer toTransformer(AccessTransformers.Entry e, String origin) {
        String owner = nameHandler.translateClassName(e.owner().replace('/', '.'));
        Target<?> target;
        if (e.isClass()) {
            target = new ClassTarget(owner);
        } else if (e.isAllFields() || e.isAllMethods()) {
            target = new WildcardTarget(owner, e.isAllMethods());
        } else if (e.isMethod()) {
            target = new MethodTarget(owner, nameHandler.translateMethodName(e.memberName()), e.methodDesc());
        } else {
            target = new FieldTarget(owner, nameHandler.translateFieldName(e.memberName()));
        }
        AccessTransformer.Modifier modifier = switch (e.change().access()) {
            case PUBLIC -> AccessTransformer.Modifier.PUBLIC;
            case PROTECTED -> AccessTransformer.Modifier.PROTECTED;
            case DEFAULT -> AccessTransformer.Modifier.DEFAULT;
            case PRIVATE -> AccessTransformer.Modifier.PRIVATE;
        };
        AccessTransformer.FinalState finalState = switch (e.change().finality()) {
            case LEAVE -> AccessTransformer.FinalState.LEAVE;
            case MAKE -> AccessTransformer.FinalState.MAKEFINAL;
            case REMOVE -> AccessTransformer.FinalState.REMOVEFINAL;
        };
        return new AccessTransformer(target, modifier, finalState, origin, e.line());
    }

    private void add(AccessTransformer at) {
        accessTransformers.merge(at.getTarget(), at, (a, b) -> a.mergeStates(b, b.getOrigin()));
    }

    /** 按类名（点分）分组的全部变换。 */
    public Map<String, List<AccessTransformer>> getAccessTransformers() {
        Map<String, List<AccessTransformer>> out = new TreeMap<>();
        for (AccessTransformer at : accessTransformers.values()) {
            out.computeIfAbsent(at.getTarget().getClassName(), k -> new ArrayList<>()).add(at);
        }
        return out;
    }

    public boolean containsClassTarget(Type type) {
        for (Target<?> t : accessTransformers.keySet()) {
            if (t.getASMType().equals(type)) {
                return true;
            }
        }
        return false;
    }

    /** 一个类上的变换，按种类分开；键是 {@link Target#targetName()}。 */
    public Map<TargetType, Map<String, AccessTransformer>> getTransformersForTarget(Type type) {
        Map<TargetType, Map<String, AccessTransformer>> out = new EnumMap<>(TargetType.class);
        for (Map.Entry<Target<?>, AccessTransformer> e : accessTransformers.entrySet()) {
            if (e.getKey().getASMType().equals(type)) {
                out.computeIfAbsent(e.getKey().getType(), k -> new HashMap<>()).put(e.getKey().targetName(),
                        e.getValue());
            }
        }
        return out;
    }

    public Set<Type> getTargets() {
        Set<Type> out = new HashSet<>();
        for (Target<?> t : accessTransformers.keySet()) {
            out.add(t.getASMType());
        }
        return out;
    }

    public void setNameHandler(INameHandler nameHandler) {
        this.nameHandler = nameHandler;
    }

    private static final class IdentityNameHandler implements INameHandler {
        @Override
        public String translateClassName(String className) {
            return className;
        }

        @Override
        public String translateFieldName(String fieldName) {
            return fieldName;
        }

        @Override
        public String translateMethodName(String methodName) {
            return methodName;
        }
    }
}
