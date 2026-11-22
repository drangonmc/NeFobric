package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;

/**
 * Forge 自己的三份配置：forge-client.toml、forge-common.toml、存档里的 forge-server.toml。
 * 模组可以直接读这些值（例如 {@code ForgeConfig.CLIENT.showLoadWarnings.get()}）。
 * 由 NeFobric 的 forge 内置模组注册（见 BuiltinModContainer）。
 */
public class ForgeConfig {
    public static class Server {
        public final ForgeConfigSpec.BooleanValue removeErroringBlockEntities;
        public final ForgeConfigSpec.BooleanValue removeErroringEntities;
        public final ForgeConfigSpec.BooleanValue fullBoundingBoxLadders;
        public final ForgeConfigSpec.DoubleValue zombieBaseSummonChance;
        public final ForgeConfigSpec.DoubleValue zombieBabyChance;
        public final ForgeConfigSpec.ConfigValue<String> permissionHandler;
        public final ForgeConfigSpec.BooleanValue advertiseDedicatedServerToLan;
        public final ForgeConfigSpec.BooleanValue useItemWithDurationZero;

        Server(ForgeConfigSpec.Builder builder) {
            builder.comment("Server configuration settings").push("server");
            removeErroringBlockEntities = builder
                    .comment("Remove a block entity that throws while ticking instead of crashing the server.",
                            "Use with care: the block entity and its data are lost.")
                    .translation("forge.configgui.removeErroringBlockEntities").worldRestart()
                    .define("removeErroringBlockEntities", false);
            removeErroringEntities = builder
                    .comment("Remove an entity that throws while ticking instead of crashing the server.",
                            "Use with care: the entity and its data are lost.")
                    .translation("forge.configgui.removeErroringEntities").worldRestart()
                    .define("removeErroringEntities", false);
            fullBoundingBoxLadders = builder
                    .comment("Look for ladders in the whole collision box of an entity, not only the block it is in.",
                            "Changes climbing behaviour, so vanilla behaviour is the default.")
                    .translation("forge.configgui.fullBoundingBoxLadders").worldRestart()
                    .define("fullBoundingBoxLadders", false);
            zombieBaseSummonChance = builder
                    .comment("Base chance for a zombie to call reinforcements.")
                    .translation("forge.configgui.zombieBaseSummonChance").worldRestart()
                    .defineInRange("zombieBaseSummonChance", 0.1D, 0.0D, 1.0D);
            zombieBabyChance = builder
                    .comment("Chance that a newly spawned zombie (or subclass) is a baby.")
                    .translation("forge.configgui.zombieBabyChance").worldRestart()
                    .defineInRange("zombieBabyChance", 0.05D, 0.0D, 1.0D);
            permissionHandler = builder
                    .comment("Permission handler used by the server. Falls back to forge:default_handler when no",
                            "handler with this name is registered.")
                    .translation("forge.configgui.permissionHandler")
                    .define("permissionHandler", "forge:default_handler");
            advertiseDedicatedServerToLan = builder
                    .comment("Announce the dedicated server on the local network so it shows up in the multiplayer list.")
                    .translation("forge.configgui.advertiseDedicatedServerToLan")
                    .define("advertiseDedicatedServerToLan", true);
            useItemWithDurationZero = builder
                    .comment("Let items with a use duration of zero be used (vanilla ignores them).")
                    .translation("forge.configgui.useItemWithDurationZero")
                    .define("useItemWithDurationZero", false);
            builder.pop();
        }
    }

    public static class Common {
        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("General configuration settings").push("general");
            builder.pop();
        }
    }

    public static class Client {
        public final ForgeConfigSpec.BooleanValue alwaysSetupTerrainOffThread;
        public final ForgeConfigSpec.BooleanValue experimentalForgeLightPipelineEnabled;
        public final ForgeConfigSpec.BooleanValue showLoadWarnings;
        public final ForgeConfigSpec.BooleanValue useCombinedDepthStencilAttachment;
        public final ForgeConfigSpec.BooleanValue compressLanIPv6Addresses;
        public final ForgeConfigSpec.BooleanValue calculateAllNormals;
        public final ForgeConfigSpec.BooleanValue stabilizeDirectionGetNearest;
        public final ForgeConfigSpec.BooleanValue allowMipmapLowering;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Client only settings, mostly things related to rendering").push("client");
            alwaysSetupTerrainOffThread = builder
                    .comment("Always build chunk meshes off the main thread. Faster, but may make some mods render wrongly.")
                    .translation("forge.configgui.alwaysSetupTerrainOffThread")
                    .define("alwaysSetupTerrainOffThread", false);
            experimentalForgeLightPipelineEnabled = builder
                    .comment("Use Forge's experimental block lighting pipeline.")
                    .translation("forge.configgui.forgeLightPipelineEnabled")
                    .define("experimentalForgeLightPipelineEnabled", false);
            showLoadWarnings = builder
                    .comment("Show the warnings screen when mods report problems while loading.")
                    .translation("forge.configgui.showLoadWarnings")
                    .define("showLoadWarnings", true);
            useCombinedDepthStencilAttachment = builder
                    .comment("Use a combined depth and stencil attachment for framebuffers.")
                    .translation("forge.configgui.useCombinedDepthStencilAttachment")
                    .define("useCombinedDepthStencilAttachment", false);
            compressLanIPv6Addresses = builder
                    .comment("Shorten IPv6 addresses in LAN broadcasts.")
                    .translation("forge.configgui.compressLanIPv6Addresses")
                    .define("compressLanIPv6Addresses", true);
            calculateAllNormals = builder
                    .comment("Recalculate normals of all model quads, not only those that need it.")
                    .translation("forge.configgui.calculateAllNormals")
                    .define("calculateAllNormals", false);
            stabilizeDirectionGetNearest = builder
                    .comment("Make Direction.getNearest stable for vectors exactly between two directions.")
                    .translation("forge.configgui.stabilizeDirectionGetNearest")
                    .define("stabilizeDirectionGetNearest", true);
            allowMipmapLowering = builder
                    .comment("Allow lowering the mipmap level when a texture is too small for the chosen level.")
                    .translation("forge.configgui.allowMipmapLowering")
                    .define("allowMipmapLowering", false);
            builder.pop();
        }

        /** 配置还没读进来时用默认值。 */
        public boolean showLoadWarnings() {
            return clientSpec.isLoaded() ? showLoadWarnings.get() : true;
        }

        public boolean calculateAllNormals() {
            return clientSpec.isLoaded() && calculateAllNormals.get();
        }

        public boolean allowMipmapLowering() {
            return clientSpec.isLoaded() && allowMipmapLowering.get();
        }
    }

    static final ForgeConfigSpec clientSpec;
    public static final Client CLIENT;
    static final ForgeConfigSpec commonSpec;
    public static final Common COMMON;
    static final ForgeConfigSpec serverSpec;
    public static final Server SERVER;

    static {
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        clientSpec = client.getRight();
        CLIENT = client.getLeft();
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        commonSpec = common.getRight();
        COMMON = common.getLeft();
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        serverSpec = server.getRight();
        SERVER = server.getLeft();
    }

    /** NeFobric 的 forge 内置模组注册配置时用。 */
    public static ForgeConfigSpec clientSpec() {
        return clientSpec;
    }

    public static ForgeConfigSpec commonSpec() {
        return commonSpec;
    }

    public static ForgeConfigSpec serverSpec() {
        return serverSpec;
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading configEvent) {
        LogManager.getLogger().debug("Loaded forge config file {}", configEvent.getConfig().getFileName());
    }

    @SubscribeEvent
    public static void onFileChange(ModConfigEvent.Reloading configEvent) {
        LogManager.getLogger().debug("Forge config just got changed on the file system!");
    }
}
