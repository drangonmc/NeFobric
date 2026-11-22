package net.fabricmc.loader.api.metadata;

import java.util.Map;

/** fabric.mod.json 里 custom 字段的值（任意 JSON）。 */
public interface CustomValue {
    CvType getType();

    CvObject getAsObject();

    CvArray getAsArray();

    String getAsString();

    Number getAsNumber();

    boolean getAsBoolean();

    interface CvObject extends CustomValue, Iterable<Map.Entry<String, CustomValue>> {
        int size();

        boolean containsKey(String key);

        CustomValue get(String key);
    }

    interface CvArray extends CustomValue, Iterable<CustomValue> {
        int size();

        CustomValue get(int index);
    }

    enum CvType {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }
}
