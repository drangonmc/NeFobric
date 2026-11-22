package net.minecraftforge.fml;

import net.minecraftforge.forgespi.language.IModInfo;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ModLoadingWarning {
    private final IModInfo modInfo;
    private final ModLoadingStage warningStage;
    private final String i18nMessage;
    private final List<Object> context;

    public ModLoadingWarning(final IModInfo modInfo, final ModLoadingStage warningStage, final String i18nMessage,
                             Object... context) {
        this.modInfo = modInfo;
        this.warningStage = warningStage;
        this.i18nMessage = i18nMessage;
        this.context = Arrays.asList(context);
    }

    public String formatToString() {
        Object[] args = Stream.concat(Stream.of(modInfo, warningStage), context.stream()).toArray();
        return Bindings.getMessageParser().get().parseMessage(i18nMessage, args);
    }
}
