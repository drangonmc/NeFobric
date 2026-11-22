package net.minecraftforge.fml.loading.progress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** 启动进度条和消息。NeFobric 没有早期加载窗口：进度条只记数，消息写日志。 */
public class StartupNotificationManager {
    private static final Logger LOGGER = LogManager.getLogger("NeFobric/Startup");
    private static final List<ProgressMeter> progressMeters = new CopyOnWriteArrayList<>();
    private static final List<Message> messages = new CopyOnWriteArrayList<>();
    private static final long start = System.nanoTime();

    public static List<ProgressMeter> getCurrentProgress() {
        return List.copyOf(progressMeters);
    }

    public static ProgressMeter prependProgressBar(final String barName, final int count) {
        ProgressMeter pm = new ProgressMeter(barName, count, 0, new Message(barName, Message.MessageType.ML));
        progressMeters.add(0, pm);
        return pm;
    }

    public static ProgressMeter addProgressBar(final String barName, final int count) {
        ProgressMeter pm = new ProgressMeter(barName, count, 0, new Message(barName, Message.MessageType.ML));
        progressMeters.add(pm);
        return pm;
    }

    public static void popBar(final ProgressMeter progressMeter) {
        progressMeters.remove(progressMeter);
    }

    public record AgeMessage(int age, Message message) {
    }

    public static List<AgeMessage> getMessages() {
        long now = System.nanoTime();
        List<AgeMessage> out = new ArrayList<>();
        for (Message m : messages) {
            out.add(new AgeMessage((int) ((now - m.timestamp()) / 1_000_000), m));
        }
        return out;
    }

    private static void add(Message.MessageType type, String message) {
        messages.add(0, new Message(message, type));
        while (messages.size() > 50) {
            messages.remove(messages.size() - 1);
        }
        LOGGER.debug("{}", message);
    }

    public static void addModMessage(final String message) {
        add(Message.MessageType.MOD, message);
    }

    public static Optional<Consumer<String>> modLoaderConsumer() {
        return Optional.of(s -> add(Message.MessageType.ML, s));
    }

    public static Optional<Consumer<String>> locatorConsumer() {
        return Optional.of(s -> add(Message.MessageType.LOC, s));
    }

    public static Optional<Consumer<String>> mcLoaderConsumer() {
        return Optional.of(s -> add(Message.MessageType.MC, s));
    }
}
