package dev.nefobric.loader.util;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 面向玩家的文字。中文系统显示简体中文，其他语言显示英文；可以用 {@code -Dnefobric.lang=zh|en} 强制指定。
 */
public final class Messages {
    private static final String BUNDLE_NAME = "nefobric.lang.messages";
    private static final ResourceBundle BUNDLE = load();

    private Messages() {
    }

    private static ResourceBundle load() {
        String forced = System.getProperty("nefobric.lang");
        Locale locale = forced == null || forced.isBlank()
                ? Locale.getDefault()
                : Locale.forLanguageTag(forced.trim().replace('_', '-'));
        // 不回退到系统默认语言：英文用户在中文系统上用 -Dnefobric.lang=en 时应该拿到英文
        ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_PROPERTIES);
        return ResourceBundle.getBundle(BUNDLE_NAME, locale, Messages.class.getClassLoader(), control);
    }

    public static String tr(String key, Object... args) {
        String pattern;
        try {
            pattern = BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return args.length == 0 ? key : key + " " + Arrays.toString(args);
        }
        if (args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, Locale.ROOT).format(args);
    }
}
