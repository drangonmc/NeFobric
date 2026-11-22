package net.minecraft.server;

import org.example.fakelib.FakeLibrary;

import java.nio.file.Path;
import java.nio.file.Paths;

/** 模拟 1.20.1 专用服务端主类：Bootstrap → 读取 server.properties → 启动。 */
public class Main {
    /** 私有：fabric-hello 的 access widener 把它改成 public，用来验证 AW 确实生效。 */
    private static String awProbe() {
        return "aw";
    }

    public static void main(String[] args) {
        System.out.println("[FakeGame] server bootstrap, nogui=" + java.util.Arrays.asList(args).contains("nogui"));
        Path properties = Paths.get("server.properties");
        ClassLoader cl = Main.class.getClassLoader();
        System.out.println("[FakeGame] server running, properties=" + properties + ", library=" + FakeLibrary.hello()
                + ", loader=" + (cl == null ? "bootstrap" : cl.getName()));
    }
}
