package net.minecraftforge.fml.loading;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FML 自带的小工具。 */
public class StringUtils {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{(\\w+)}");

    public static String toLowerCase(String str) {
        return str.toLowerCase(Locale.ROOT);
    }

    public static String toUpperCase(String str) {
        return str.toUpperCase(Locale.ROOT);
    }

    public static boolean endsWith(String search, String... endings) {
        String lc = toLowerCase(search);
        for (String e : endings) {
            if (lc.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    public static URL toURL(String string) {
        if (string == null || string.trim().isEmpty() || string.contains("myurl.me") || string.contains("example.invalid")) {
            return null;
        }
        try {
            return new URL(string);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String parseStringFormat(String input, Map<String, String> properties) {
        Matcher m = PROPERTY.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = properties.getOrDefault(m.group(1), m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String binToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
