package net.minecraftforge.fml;

import net.minecraftforge.fml.loading.EarlyLoadingException;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ModLoadingException extends RuntimeException {
    private static final long serialVersionUID = 2048947398536935507L;
    private final IModInfo modInfo;
    private final ModLoadingStage errorStage;
    private final String i18nMessage;
    private final List<Object> context;

    public ModLoadingException(final IModInfo modInfo, final ModLoadingStage errorStage, final String i18nMessage,
                               final Throwable originalException, Object... context) {
        super("Mod Loading Exception", originalException);
        this.modInfo = modInfo;
        this.errorStage = errorStage;
        this.i18nMessage = i18nMessage;
        this.context = Arrays.asList(context);
    }

    static Stream<ModLoadingException> fromEarlyException(final EarlyLoadingException e) {
        return e.getAllData().stream().map(ed -> new ModLoadingException(ed.getModInfo(), ModLoadingStage.VALIDATE,
                ed.getI18message(), e.getCause(), ed.getArgs()));
    }

    public String getI18NMessage() {
        return i18nMessage;
    }

    public Object[] getContext() {
        return context.toArray();
    }

    public ModLoadingStage getErrorStage() {
        return errorStage;
    }

    public String formatToString() {
        Object[] args = Stream.concat(Stream.of(modInfo, errorStage, getCause()), context.stream()).toArray();
        return Bindings.getMessageParser().get().parseMessage(i18nMessage, args);
    }

    @Override
    public String getMessage() {
        return formatToString();
    }

    public IModInfo getModInfo() {
        return modInfo;
    }

    public String getCleanMessage() {
        return Bindings.getMessageParser().get().stripControlCodes(formatToString());
    }
}
