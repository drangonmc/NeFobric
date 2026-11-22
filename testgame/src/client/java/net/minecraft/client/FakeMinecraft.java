package net.minecraft.client;

import java.io.File;

/**
 * 代替原版的游戏对象。构造函数的开头和原版 {@code Minecraft} 一样：先登记实例，再给游戏目录（第一个 File 字段）赋值，
 * NeFobric 的客户端钩子就插在这之后。
 */
public class FakeMinecraft {
    public static boolean bootstrapped;
    private static FakeMinecraft instance;
    public final File gameDirectory;

    public FakeMinecraft(String[] args) {
        instance = this;
        this.gameDirectory = new File(gameDirArg(args));
        System.out.println("[FakeGame] constructing game, args=" + args.length);
    }

    public static FakeMinecraft getInstance() {
        return instance;
    }

    private static String gameDirArg(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--gameDir")) {
                return args[i + 1];
            }
        }
        return ".";
    }

    /** 私有：fabric-hello 的 access widener 把它改成 public，用来验证 AW 确实生效。 */
    private static String awProbe() {
        return "aw";
    }

    public void run() {
        ClassLoader cl = FakeMinecraft.class.getClassLoader();
        System.out.println("[FakeGame] client running, loader=" + (cl == null ? "bootstrap" : cl.getName()));
    }
}
