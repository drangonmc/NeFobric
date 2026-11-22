package net.minecraftforge.forgespi.language;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** 模组语言提供者（javafml、lowcodefml 等）。NeFobric 自己负责加载，这个接口只为签名兼容。 */
public interface IModLanguageProvider {
    String name();

    Consumer<ModFileScanData> getFileVisitor();

    <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(Supplier<R> consumeEvent);

    interface IModLanguageLoader {
        <T> T loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer);
    }
}
