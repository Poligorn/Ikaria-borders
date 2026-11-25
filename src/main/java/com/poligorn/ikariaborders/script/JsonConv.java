package com.poligorn.ikariaborders.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/** Converts script values (string, number, boolean, nested maps/lists) to JSON for zone data. Pure JVM. */
public final class JsonConv {
    private static final int MAX_DEPTH = 8;

    private JsonConv() {}

    /** @throws IllegalArgumentException for unsupported values (null, NaN, functions, Java objects, too deep) */
    public static JsonElement toJson(Object v) {
        return toJson(v, 0);
    }

    private static JsonElement toJson(Object v, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("слишком глубокая вложенность (максимум " + MAX_DEPTH + ")");
        if (v == null) throw new IllegalArgumentException("null не поддерживается (используйте remove)");
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new IllegalArgumentException("число должно быть конечным");
            return new JsonPrimitive(n);
        }
        if (v instanceof CharSequence cs) return new JsonPrimitive(cs.toString());
        if (v instanceof Map<?, ?> m) {
            JsonObject o = new JsonObject();
            for (var e : m.entrySet()) o.add(String.valueOf(e.getKey()), toJson(e.getValue(), depth + 1));
            return o;
        }
        if (v instanceof Iterable<?> it) {
            JsonArray a = new JsonArray();
            for (Object x : it) a.add(toJson(x, depth + 1));
            return a;
        }
        if (v instanceof Object[] arr) {
            JsonArray a = new JsonArray();
            for (Object x : arr) a.add(toJson(x, depth + 1));
            return a;
        }
        throw new IllegalArgumentException("неподдерживаемый тип значения: " + v.getClass().getSimpleName());
    }
}
