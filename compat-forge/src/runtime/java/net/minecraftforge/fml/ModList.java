package net.minecraftforge.fml;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.minecraftforge.forgespi.locating.IModFile;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 已加载的 Forge 系模组（含 minecraft 和 forge 两个虚拟模组）。 */
public class ModList {
    private static ModList INSTANCE;

    private final List<IModFileInfo> modFiles;
    private final List<IModInfo> sortedList;
    private final Map<String, ModFileInfo> fileById;
    private List<ModContainer> mods = List.of();
    private Map<String, ModContainer> indexedMods = Map.of();
    private List<ModFileScanData> modFileScanData;
    private List<ModContainer> sortedContainers = List.of();

    private ModList(final List<ModFile> modFiles, final List<ModInfo> sortedList) {
        this.modFiles = modFiles.stream().map(ModFile::getModFileInfo).map(IModFileInfo.class::cast)
                .collect(Collectors.toList());
        this.sortedList = new ArrayList<>(sortedList);
        this.fileById = new LinkedHashMap<>();
        for (IModFileInfo f : this.modFiles) {
            for (IModInfo m : f.getMods()) {
                fileById.putIfAbsent(m.getModId(), (ModFileInfo) f);
            }
        }
        CrashReportCallables.registerCrashCallable("Mod List", this::crashReport);
    }

    private String getModContainerState(String modId) {
        return getModContainerById(modId).map(ModContainer::getCurrentState).map(Object::toString).orElse("NONE");
    }

    private String fileToLine(IModFile mf) {
        IModInfo first = mf.getModInfos().isEmpty() ? null : mf.getModInfos().get(0);
        if (first == null) {
            return mf.getFileName();
        }
        return String.format(Locale.ENGLISH, "%-50.50s|%-30.30s|%-30.30s|%-20.20s|%-10.10s|Manifest: %s",
                mf.getFileName(), first.getDisplayName(), first.getModId(), first.getVersion(),
                getModContainerState(first.getModId()), "NOSIGNATURE");
    }

    private String crashReport() {
        return "\n" + applyForEachModFile(this::fileToLine).collect(Collectors.joining("\n\t\t", "\t\t", ""));
    }

    public static ModList of(List<ModFile> modFiles, List<ModInfo> sortedList) {
        INSTANCE = new ModList(modFiles, sortedList);
        return INSTANCE;
    }

    public static ModList get() {
        return INSTANCE;
    }

    public List<IModFileInfo> getModFiles() {
        return modFiles;
    }

    public IModFileInfo getModFileById(String modid) {
        return this.fileById.get(modid);
    }

    <T extends Event & IModBusEvent> Function<Executor, CompletableFuture<Void>> futureVisitor(
            final IModStateTransition.EventGenerator<T> eventGenerator,
            final ProgressMeter progressBar,
            final BiFunction<ModLoadingStage, Throwable, ModLoadingStage> stateChange) {
        return executor -> gather(this.mods.stream()
                .map(mod -> ModContainer.buildTransitionHandler(mod, eventGenerator, progressBar, stateChange, executor))
                .collect(Collectors.toList()))
                .thenComposeAsync(ModList::completableFutureFromExceptionList, executor);
    }

    static CompletionStage<Void> completableFutureFromExceptionList(List<? extends Map.Entry<?, Throwable>> t) {
        if (t.stream().noneMatch(e -> e.getValue() != null)) {
            return CompletableFuture.completedFuture(null);
        }
        RuntimeException accumulator = new RuntimeException();
        t.stream().map(Map.Entry::getValue).filter(Objects::nonNull).forEach(exception -> {
            if (exception instanceof CompletionException && exception.getCause() != null) {
                exception = exception.getCause();
            }
            if (exception.getSuppressed().length != 0) {
                Arrays.stream(exception.getSuppressed()).forEach(accumulator::addSuppressed);
            } else {
                accumulator.addSuppressed(exception);
            }
        });
        return CompletableFuture.failedFuture(accumulator);
    }

    static <V> CompletableFuture<List<Map.Entry<V, Throwable>>> gather(
            List<? extends CompletableFuture<? extends V>> futures) {
        List<Map.Entry<V, Throwable>> list = new ArrayList<>(futures.size());
        CompletableFuture<?>[] results = new CompletableFuture[futures.size()];
        for (int i = 0; i < futures.size(); i++) {
            int idx = i;
            list.add(null);
            results[i] = futures.get(i).whenComplete((result, exception) ->
                    list.set(idx, new AbstractMap.SimpleImmutableEntry<>(result, exception)));
        }
        return CompletableFuture.allOf(results).handle((r, th) -> null).thenApply(res -> list);
    }

    void setLoadedMods(final List<ModContainer> modContainers) {
        this.mods = List.copyOf(modContainers);
        this.sortedContainers = modContainers.stream()
                .sorted(Comparator.comparingInt(c -> sortedList.indexOf(c.getModInfo())))
                .toList();
        Map<String, ModContainer> index = new LinkedHashMap<>();
        modContainers.forEach(c -> index.putIfAbsent(c.getModId(), c));
        this.indexedMods = index;
    }

    /** NeFobric：按加载顺序的容器列表。 */
    List<ModContainer> containersInOrder() {
        return sortedContainers;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getModObjectById(String modId) {
        return getModContainerById(modId).map(ModContainer::getMod).map(o -> (T) o);
    }

    public Optional<? extends ModContainer> getModContainerById(String modId) {
        return Optional.ofNullable(this.indexedMods.get(modId));
    }

    public Optional<? extends ModContainer> getModContainerByObject(Object obj) {
        return mods.stream().filter(mc -> mc.getMod() == obj).findFirst();
    }

    public List<IModInfo> getMods() {
        return this.sortedList;
    }

    /**
     * 模组是否已加载。容器还没建好时（例如模组在类初始化里提前问）按模组信息回答，和 Forge 的 FMLLoader
     * 阶段行为一致。
     */
    public boolean isLoaded(String modTarget) {
        if (!indexedMods.isEmpty()) {
            return indexedMods.containsKey(modTarget);
        }
        return fileById.containsKey(modTarget);
    }

    public int size() {
        return mods.size();
    }

    public List<ModFileScanData> getAllScanData() {
        if (modFileScanData == null) {
            modFileScanData = this.sortedList.stream()
                    .map(IModInfo::getOwningFile)
                    .filter(Objects::nonNull)
                    .map(IModFileInfo::getFile)
                    .distinct()
                    .map(IModFile::getScanResult)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return modFileScanData;
    }

    public void forEachModFile(Consumer<IModFile> fileConsumer) {
        modFiles.stream().map(IModFileInfo::getFile).forEach(fileConsumer);
    }

    public <T> Stream<T> applyForEachModFile(Function<IModFile, T> function) {
        return modFiles.stream().map(IModFileInfo::getFile).map(function);
    }

    public void forEachModContainer(BiConsumer<String, ModContainer> modContainerConsumer) {
        indexedMods.forEach(modContainerConsumer);
    }

    public void forEachModInOrder(Consumer<ModContainer> containerConsumer) {
        this.sortedContainers.forEach(containerConsumer);
    }

    public <T> Stream<T> applyForEachModContainer(Function<ModContainer, T> function) {
        return indexedMods.values().stream().map(function);
    }

    static Collection<ModContainer> all() {
        return INSTANCE == null ? List.of() : INSTANCE.mods;
    }
}
