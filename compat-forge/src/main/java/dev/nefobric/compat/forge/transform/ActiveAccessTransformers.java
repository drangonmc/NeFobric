package dev.nefobric.compat.forge.transform;

import java.util.ArrayList;
import java.util.List;

/**
 * 这次启动生效的访问变换：启动时从模组 jar 里读到的 AT 文件，加上运行中用 {@code FMLLoader.addAccessTransformer}
 * 追加的。Forge 运行时的 {@code AccessTransformerEngine} 从这里取原文；ACCESS 阶段的 {@link AccessTransformerApplier}
 * 跟着更新，追加的修改对之后才加载的类生效（和 Forge 一样，已经加载的类不会变）。
 */
public final class ActiveAccessTransformers {
    /** 一个 AT 文件的原文和出处（模组 id 或文件名）。 */
    public record Source(String text, String origin) {
    }

    private static final List<Source> SOURCES = new ArrayList<>();
    private static AccessTransformerApplier applier;

    private ActiveAccessTransformers() {
    }

    /** 启动时由 ForgeAdapter 调用一次。 */
    public static synchronized void install(List<Source> sources, AccessTransformerApplier target) {
        SOURCES.clear();
        SOURCES.addAll(sources);
        applier = target;
    }

    public static synchronized List<Source> sources() {
        return List.copyOf(SOURCES);
    }

    /**
     * 运行中追加一个 AT 文件。
     *
     * @return 这个文件里写错、被跳过的行
     */
    public static synchronized List<String> add(String text, String origin) {
        List<String> problems = new ArrayList<>();
        AccessTransformers.parseEntries(text, origin, problems);
        SOURCES.add(new Source(text, origin));
        if (applier != null) {
            applier.update(build(SOURCES));
        }
        return problems;
    }

    /** 合并这些文件；写错的行跳过。 */
    public static AccessTransformers build(List<Source> sources) {
        AccessTransformers.Builder b = AccessTransformers.builder();
        for (Source s : sources) {
            b.parse(s.text(), s.origin());
        }
        return b.build();
    }
}
