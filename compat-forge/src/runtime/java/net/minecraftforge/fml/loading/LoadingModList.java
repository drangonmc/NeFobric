package net.minecraftforge.fml.loading;

import dev.nefobric.forge.fml.ForgeEnvironment;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 已发现、已排好序的 Forge 系模组（游戏开始前就确定）。 */
public class LoadingModList {
    private static LoadingModList INSTANCE;

    private final List<ModFileInfo> modFiles;
    private final List<ModInfo> sortedList;
    private final Map<String, ModFileInfo> fileById;

    private LoadingModList(List<ModFileInfo> modFiles, List<ModInfo> sortedList) {
        this.modFiles = List.copyOf(modFiles);
        this.sortedList = List.copyOf(sortedList);
        this.fileById = sortedList.stream().collect(Collectors.toMap(ModInfo::getModId, ModInfo::getOwningFile,
                (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /** 由 NeFobric 的 Forge 运行时在模组构造前创建一次。 */
    public static LoadingModList create(List<ModFileInfo> modFiles, List<ModInfo> sortedList) {
        INSTANCE = new LoadingModList(modFiles, sortedList);
        return INSTANCE;
    }

    public static LoadingModList get() {
        if (INSTANCE == null) {
            ForgeEnvironment.ensureModList();
        }
        return INSTANCE;
    }

    public List<ModFileInfo> getModFiles() {
        return modFiles;
    }

    public List<ModInfo> getMods() {
        return sortedList;
    }

    public ModFileInfo getModFileById(String modid) {
        return fileById.get(modid);
    }

    public Path findResource(String className) {
        for (ModFileInfo mf : modFiles) {
            Path p = mf.getFile().findResource(className);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    public Enumeration<URL> findAllURLsForResource(String resName) {
        List<URL> out = new ArrayList<>();
        for (ModFileInfo mf : modFiles) {
            Path p = mf.getFile().findResource(resName);
            if (Files.exists(p)) {
                try {
                    out.add(p.toUri().toURL());
                } catch (IOException ignored) {
                    // 跳过无法转成 URL 的路径
                }
            }
        }
        return Collections.enumeration(out);
    }

    public List<EarlyLoadingException> getErrors() {
        return List.of();
    }

    public List<?> getBrokenFiles() {
        return List.of();
    }
}
