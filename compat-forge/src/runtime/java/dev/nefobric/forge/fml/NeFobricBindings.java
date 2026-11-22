package dev.nefobric.forge.fml;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.I18NParser;
import net.minecraftforge.fml.IBindingsProvider;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FML 核心需要的 Forge 本体部分：Forge 总线、加载错误消息的格式化、配置事件的构造。 */
public final class NeFobricBindings implements IBindingsProvider {
    @Override
    public Supplier<IEventBus> getForgeBusSupplier() {
        return () -> MinecraftForge.EVENT_BUS;
    }

    @Override
    public Supplier<I18NParser> getMessageParser() {
        return () -> MessageParser.INSTANCE;
    }

    @Override
    public Supplier<IConfigEvent.ConfigConfig> getConfigConfiguration() {
        return () -> new IConfigEvent.ConfigConfig(ModConfigEvent.Loading::new, ModConfigEvent.Reloading::new,
                ModConfigEvent.Unloading::new);
    }

    /**
     * 加载错误消息。游戏语言文件加载之前就可能要格式化，所以内置一份英文文本；占位符写法和 Forge 的一样
     * （{@code {0,modinfo,id}}、{@code {2,exc,msg}}、{@code {1,lower}}……）。
     */
    static final class MessageParser implements I18NParser {
        static final MessageParser INSTANCE = new MessageParser();
        private static final Pattern ARG = Pattern.compile("\\{(\\d+)(?:,([a-z0-9]+)(?:,([^}]*))?)?}");
        private static final Pattern CONTROL = Pattern.compile("(?i)§[0-9A-FK-OR]");
        private static final Map<String, String> MESSAGES = Map.ofEntries(
                Map.entry("fml.modloading.missingclasses", "Mod file {3} declares mods whose classes are missing"),
                Map.entry("fml.modloading.missingmetadata", "mods.toml has no entry for mod id {3}"),
                Map.entry("fml.modloading.failedtoloadmodclass", "{0,modinfo,name} could not load its classes\n{2,exc,msg}"),
                Map.entry("fml.modloading.failedtoloadmod", "{0,modinfo,name} ({0,modinfo,id}) failed to load\n{2,exc,msg}"),
                Map.entry("fml.modloading.feature.missing", "{0,modinfo,name} ({0,modinfo,id}) needs {3,featurebound}, but {4} is available"),
                Map.entry("fml.modloading.uncaughterror", "Uncaught error while loading mods\n{2,exc,msg}"),
                Map.entry("fml.modloading.errorduringevent", "{0,modinfo,name} ({0,modinfo,id}) threw an error during the {1,lower} phase\n{2,exc,msg}"),
                Map.entry("fml.modloading.failedtoprocesswork", "{0,modinfo,name} ({0,modinfo,id}) threw an error in deferred work\n{2,exc,msg}"),
                Map.entry("fml.modloading.brokenfile", "File {2} is not a valid mod file"),
                Map.entry("fml.modloading.brokenresources", "File {2} has no valid resource pack metadata"),
                Map.entry("fml.modloading.missinglicense", "File {3} has no license information"));

        @Override
        public String parseMessage(String i18nMessage, Object... args) {
            String pattern = MESSAGES.getOrDefault(i18nMessage, i18nMessage);
            Matcher m = ARG.matcher(pattern);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                int idx = Integer.parseInt(m.group(1));
                Object arg = idx < args.length ? args[idx] : null;
                m.appendReplacement(sb, Matcher.quoteReplacement(format(arg, m.group(2), m.group(3))));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        private static String format(Object arg, String type, String param) {
            if (type == null) {
                return String.valueOf(arg);
            }
            switch (type) {
                case "modinfo":
                    if (arg instanceof IModInfo info) {
                        return "name".equals(param) ? info.getDisplayName() : info.getModId();
                    }
                    return String.valueOf(arg);
                case "lower":
                    return String.valueOf(arg).toLowerCase(Locale.ROOT);
                case "upper":
                    return String.valueOf(arg).toUpperCase(Locale.ROOT);
                case "exc":
                    if (arg instanceof Throwable t) {
                        return "cls".equals(param) ? t.getClass().getName() : String.valueOf(t);
                    }
                    return String.valueOf(arg);
                case "vr":
                    return arg instanceof VersionRange vr ? vr.toString() : String.valueOf(arg);
                case "i18n":
                    return arg == null ? String.valueOf(param) : String.valueOf(arg);
                default:
                    return String.valueOf(arg);
            }
        }

        @Override
        public String stripControlCodes(String toStrip) {
            return toStrip == null ? null : CONTROL.matcher(toStrip).replaceAll("");
        }
    }
}
