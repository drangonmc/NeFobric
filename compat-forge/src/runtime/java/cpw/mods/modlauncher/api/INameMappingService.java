package cpw.mods.modlauncher.api;

import java.util.Map;
import java.util.function.BiFunction;

/** ModLauncher 的名字映射服务。Forge 用它在开发环境把 SRG 名换成可读名；生产环境（NeFobric）不做映射。 */
public interface INameMappingService {
    String mappingName();

    String mappingVersion();

    Map.Entry<String, String> understanding();

    BiFunction<Domain, String, String> namingFunction();

    enum Domain {
        CLASS, METHOD, FIELD
    }
}
