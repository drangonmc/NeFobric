package dev.nefobric.testmods.jij;

import dev.nefobric.testmods.jij.lib.JijLib;
import dev.nefobric.testmods.jij.plain.PlainLib;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

public class JijHost implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        System.out.println("[fabric_jij] host: " + JijLib.greet()
                + ", plain=" + PlainLib.value()
                + ", libLoaded=" + loader.isModLoaded("fabric_jij_lib")
                + ", deepLoaded=" + loader.isModLoaded("fabric_jij_deep"));
        // Fabric 的 ModContainer 视图：内嵌模组的来源是 NESTED，指向父模组和在父 jar 里的路径
        System.out.println("[fabric_jij] containers: " + describe(loader, "fabric_jij")
                + "; " + describe(loader, "fabric_jij_lib") + "; " + describe(loader, "fabric_jij_deep"));
    }

    private static String describe(FabricLoader loader, String id) {
        ModContainer mod = loader.getModContainer(id).orElseThrow();
        ModOrigin origin = mod.getOrigin();
        String where = origin.getKind() == ModOrigin.Kind.NESTED
                ? "nested in " + origin.getParentModId() + " at " + origin.getParentSubLocation()
                : origin.getKind().name();
        String parent = mod.getContainingMod().map(m -> m.getMetadata().getId()).orElse("-");
        String children = mod.getContainedMods().stream().map(m -> m.getMetadata().getId()).sorted().toList().toString();
        return id + " " + where + ", parent=" + parent + ", children=" + children;
    }
}
