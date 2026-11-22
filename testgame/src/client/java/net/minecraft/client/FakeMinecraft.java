package net.minecraft.client;

/** 代替原版的游戏对象。 */
public class FakeMinecraft {
    public static boolean bootstrapped;

    public FakeMinecraft(String[] args) {
        System.out.println("[FakeGame] constructing game, args=" + args.length);
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
