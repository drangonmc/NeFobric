package net.minecraftforge.forgespi.locating;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** 一个模组 jar。 */
public interface IModFile {
    List<IModLanguageProvider> getLoaders();

    Path findResource(String... pathName);

    Supplier<Map<String, Object>> getSubstitutionMap();

    Type getType();

    Path getFilePath();

    SecureJar getSecureJar();

    void setSecurityStatus(SecureJar.Status status);

    List<IModInfo> getModInfos();

    ModFileScanData getScanResult();

    String getFileName();

    IModProvider getProvider();

    IModFileInfo getModFileInfo();

    enum Type {
        MOD,
        LIBRARY,
        LANGPROVIDER,
        GAMELIBRARY
    }
}
