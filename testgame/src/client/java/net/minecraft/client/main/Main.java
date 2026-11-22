package net.minecraft.client.main;

import net.minecraft.client.FakeMinecraft;
import net.minecraft.client.FakeSilentInitException;

import java.io.File;

/**
 * 模拟 1.20.1 客户端主类的结构：Bootstrap → 切到 “Render thread” → 构造游戏对象 → run。
 * 和原版一样，初始化阶段的异常会生成 “Initializing game” 崩溃报告并退出；原版写报告时要用
 * {@code Minecraft.getInstance().gameDirectory}，这里也一样，所以钩子位置不对时会像原版那样连锁报 NPE。
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("[FakeGame] client bootstrap on thread " + Thread.currentThread().getName());
        FakeMinecraft.bootstrapped = true;
        final FakeMinecraft minecraft;
        try {
            Thread.currentThread().setName("Render thread");
            minecraft = new FakeMinecraft(args);
        } catch (FakeSilentInitException e) {
            System.out.println("[FakeGame] failed to create window");
            return;
        } catch (Throwable t) {
            File reports = new File(FakeMinecraft.getInstance().gameDirectory, "crash-reports");
            System.out.println("[FakeGame] crash report: Initializing game: " + t + " -> " + reports);
            System.exit(-1);
            return;
        }
        minecraft.run();
    }
}
