package dev.nefobric.loader.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 三种映射文件的读取器，结果都以官方名为键写进 {@link MappingData.Builder}：
 * <ul>
 *   <li>Mojang 官方映射（ProGuard 格式，{@code client.txt}/{@code server.txt}）：只取类名，作为运行时的类名；</li>
 *   <li>MCPConfig 的 {@code joined.tsrg}（TSRG2 或旧版 TSRG）：成员的 SRG 名；</li>
 *   <li>Fabric 的 intermediary（Tiny v2）：类和成员的 intermediary 名。</li>
 * </ul>
 */
public final class MappingReaders {

    private MappingReaders() {
    }

    /**
     * ProGuard 格式：{@code net.minecraft.client.Minecraft -> enn:}。成员行以空白开头，这里不需要。
     *
     * @return 读到的类数
     */
    public static int readProguardClasses(BufferedReader in, MappingData.Builder out, String ns) throws IOException {
        int count = 0;
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#' || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            int arrow = line.indexOf(" -> ");
            if (arrow < 0 || !line.endsWith(":")) {
                continue;
            }
            String named = line.substring(0, arrow).trim().replace('.', '/');
            String official = line.substring(arrow + 4, line.length() - 1).trim().replace('.', '/');
            out.putClass(official, ns, named);
            count++;
        }
        return count;
    }

    /**
     * TSRG2（首行 {@code tsrg2 obf srg id}）或旧版 TSRG（没有首行）。类行没有缩进；成员行缩进一个制表符：
     * 字段是 {@code obf srg [id]} 或 {@code obf desc srg [id]}，方法是 {@code obf (desc) srg [id]}；
     * 缩进两个制表符的是参数和 {@code static} 标记，跳过。
     *
     * @param classNs 类名写到哪个命名空间；{@code null} 表示不要类名（由 Mojang 映射提供）
     * @return 读到的成员数
     */
    public static int readTsrg(BufferedReader in, MappingData.Builder out, String classNs, String memberNs)
            throws IOException {
        String first = in.readLine();
        if (first == null) {
            return 0;
        }
        int nsCount = 2;
        int srgCol = 1;
        List<String> pending = new ArrayList<>();
        if (first.startsWith("tsrg2 ")) {
            String[] header = first.substring(6).trim().split("\\s+");
            nsCount = header.length;
            srgCol = indexOf(header, "srg", 1);
        } else {
            pending.add(first);
        }
        int count = 0;
        String currentClass = null;
        int idx = 0;
        while (true) {
            String line = idx < pending.size() ? pending.get(idx++) : in.readLine();
            if (line == null) {
                break;
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("\t\t")) {
                continue;
            }
            if (line.charAt(0) == '\t') {
                if (currentClass == null) {
                    continue;
                }
                String[] t = line.substring(1).trim().split("\\s+");
                if (t.length == nsCount) {
                    // 字段，无描述符
                    out.putField(currentClass, t[0], memberNs, t[srgCol]);
                    count++;
                } else if (t.length == nsCount + 1) {
                    if (t[1].startsWith("(")) {
                        out.putMethod(currentClass, t[0], t[1], memberNs, t[srgCol + 1]);
                    } else {
                        out.putField(currentClass, t[0], memberNs, t[srgCol + 1]);
                    }
                    count++;
                }
                continue;
            }
            String[] t = line.trim().split("\\s+");
            if (t.length < 2) {
                continue;
            }
            currentClass = t[0];
            if (classNs != null) {
                out.putClass(currentClass, classNs, t[srgCol]);
            }
        }
        return count;
    }

    /**
     * Tiny v2：首行 {@code tiny 2 0 official intermediary ...}，列用制表符分隔。{@code c} 是类，缩进一级的
     * {@code f}/{@code m} 是字段和方法（描述符写在第一个命名空间里），更深的缩进是参数、局部变量和注释。
     *
     * @return 读到的条目数（类 + 成员）
     */
    public static int readTiny2(BufferedReader in, MappingData.Builder out, String fileNs, String ns)
            throws IOException {
        String header = in.readLine();
        if (header == null || !header.startsWith("tiny\t2\t")) {
            throw new IOException("Not a Tiny v2 file");
        }
        String[] h = header.split("\t");
        List<String> fileNamespaces = List.of(h).subList(3, h.length);
        int official = fileNamespaces.indexOf(Mappings.OFFICIAL);
        int target = fileNamespaces.indexOf(fileNs);
        if (official < 0 || target < 0) {
            throw new IOException("Tiny file has namespaces " + fileNamespaces + ", need official and " + fileNs);
        }
        if (official != 0) {
            throw new IOException("Tiny file must list official first, has " + fileNamespaces);
        }
        int count = 0;
        String currentClass = null;
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("\t\t")) {
                continue;
            }
            if (line.charAt(0) == '\t') {
                if (currentClass == null) {
                    continue;
                }
                String[] t = line.substring(1).split("\t", -1);
                if (t.length < 3) {
                    continue;
                }
                // f/m desc name0 name1 ...
                String kind = t[0];
                String desc = t[1];
                String offName = t[2];
                String name = 2 + target < t.length ? t[2 + target] : "";
                if (name.isEmpty()) {
                    name = offName;
                }
                if ("f".equals(kind)) {
                    out.putField(currentClass, offName, ns, name);
                    count++;
                } else if ("m".equals(kind)) {
                    out.putMethod(currentClass, offName, desc, ns, name);
                    count++;
                }
                continue;
            }
            String[] t = line.split("\t", -1);
            if ("c".equals(t[0]) && t.length > 1) {
                currentClass = t[1];
                String name = 1 + target < t.length ? t[1 + target] : "";
                out.putClass(currentClass, ns, name.isEmpty() ? currentClass : name);
                count++;
            } else {
                currentClass = null;
            }
        }
        return count;
    }

    private static int indexOf(String[] a, String s, int fallback) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals(s)) {
                return i;
            }
        }
        return fallback;
    }
}
