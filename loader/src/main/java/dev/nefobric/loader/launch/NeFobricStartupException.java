package dev.nefobric.loader.launch;

import java.util.List;

/** 启动前就发现的问题（缺依赖、找不到游戏等）。每一条都是给玩家看的完整句子。 */
public class NeFobricStartupException extends RuntimeException {
    private final List<String> problems;

    public NeFobricStartupException(List<String> problems) {
        super(String.join("\n", problems));
        this.problems = List.copyOf(problems);
    }

    public NeFobricStartupException(String problem) {
        this(List.of(problem));
    }

    public List<String> problems() {
        return problems;
    }
}
