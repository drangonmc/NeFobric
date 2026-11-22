package dev.nefobric.compat.fabric.metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.metadata.CustomValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** fabric.mod.json 里 custom 值的不可变实现。类型不符时抛 {@link ClassCastException}，与 Fabric Loader 相同。 */
public abstract class CustomValueImpl implements CustomValue {
    public static final CustomValue BOOLEAN_TRUE = new BooleanImpl(true);
    public static final CustomValue BOOLEAN_FALSE = new BooleanImpl(false);
    public static final CustomValue NULL = new NullImpl();

    public static CustomValue fromJson(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return NULL;
        }
        if (e.isJsonObject()) {
            Map<String, CustomValue> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) e).entrySet()) {
                map.put(entry.getKey(), fromJson(entry.getValue()));
            }
            return new ObjectImpl(map);
        }
        if (e.isJsonArray()) {
            List<CustomValue> list = new ArrayList<>();
            for (JsonElement item : (JsonArray) e) {
                list.add(fromJson(item));
            }
            return new ArrayImpl(list);
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return p.getAsBoolean() ? BOOLEAN_TRUE : BOOLEAN_FALSE;
        }
        if (p.isNumber()) {
            return new NumberImpl(toNumber(p.getAsString()));
        }
        return new StringImpl(p.getAsString());
    }

    /** 与 Fabric Loader 一样把 JSON 数字读成 double。 */
    private static Number toNumber(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return new java.math.BigDecimal(s);
        }
    }

    @Override
    public final CvObject getAsObject() {
        if (this instanceof ObjectImpl o) {
            return o;
        }
        throw new ClassCastException("can't convert " + getType().name() + " to Object");
    }

    @Override
    public final CvArray getAsArray() {
        if (this instanceof ArrayImpl a) {
            return a;
        }
        throw new ClassCastException("can't convert " + getType().name() + " to Array");
    }

    @Override
    public final String getAsString() {
        if (this instanceof StringImpl s) {
            return s.value;
        }
        throw new ClassCastException("can't convert " + getType().name() + " to String");
    }

    @Override
    public final Number getAsNumber() {
        if (this instanceof NumberImpl n) {
            return n.value;
        }
        throw new ClassCastException("can't convert " + getType().name() + " to Number");
    }

    @Override
    public final boolean getAsBoolean() {
        if (this instanceof BooleanImpl b) {
            return b.value;
        }
        throw new ClassCastException("can't convert " + getType().name() + " to Boolean");
    }

    public static final class ObjectImpl extends CustomValueImpl implements CvObject {
        private final Map<String, CustomValue> entries;

        public ObjectImpl(Map<String, CustomValue> entries) {
            this.entries = Collections.unmodifiableMap(entries);
        }

        @Override
        public CvType getType() {
            return CvType.OBJECT;
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean containsKey(String key) {
            return entries.containsKey(key);
        }

        @Override
        public CustomValue get(String key) {
            return entries.get(key);
        }

        @Override
        public Iterator<Map.Entry<String, CustomValue>> iterator() {
            return entries.entrySet().iterator();
        }

        @Override
        public String toString() {
            return entries.toString();
        }
    }

    public static final class ArrayImpl extends CustomValueImpl implements CvArray {
        private final List<CustomValue> entries;

        public ArrayImpl(List<CustomValue> entries) {
            this.entries = Collections.unmodifiableList(entries);
        }

        @Override
        public CvType getType() {
            return CvType.ARRAY;
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public CustomValue get(int index) {
            return entries.get(index);
        }

        @Override
        public Iterator<CustomValue> iterator() {
            return entries.iterator();
        }

        @Override
        public String toString() {
            return entries.toString();
        }
    }

    public static final class StringImpl extends CustomValueImpl {
        final String value;

        public StringImpl(String value) {
            this.value = value;
        }

        @Override
        public CvType getType() {
            return CvType.STRING;
        }

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    public static final class NumberImpl extends CustomValueImpl {
        final Number value;

        public NumberImpl(Number value) {
            this.value = value;
        }

        @Override
        public CvType getType() {
            return CvType.NUMBER;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public static final class BooleanImpl extends CustomValueImpl {
        final boolean value;

        BooleanImpl(boolean value) {
            this.value = value;
        }

        @Override
        public CvType getType() {
            return CvType.BOOLEAN;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public static final class NullImpl extends CustomValueImpl {
        NullImpl() {
        }

        @Override
        public CvType getType() {
            return CvType.NULL;
        }

        @Override
        public String toString() {
            return "null";
        }
    }
}
