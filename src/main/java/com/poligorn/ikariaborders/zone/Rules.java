package com.poligorn.ikariaborders.zone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helpers for rule trees (nested JSON objects). */
public final class Rules {
    private Rules() {}

    /** Deep merge: values from {@code over} win; nested objects merge recursively. Inputs are not modified. */
    public static JsonObject merge(JsonObject base, JsonObject over) {
        JsonObject out = base.deepCopy();
        for (Map.Entry<String, JsonElement> e : over.entrySet()) {
            JsonElement cur = out.get(e.getKey());
            if (cur != null && cur.isJsonObject() && e.getValue().isJsonObject()) {
                out.add(e.getKey(), merge(cur.getAsJsonObject(), e.getValue().getAsJsonObject()));
            } else {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        return out;
    }

    /** Looks up a dotted path ("claims.allow"); returns null if unset. */
    public static JsonElement lookup(JsonObject rules, String path) {
        JsonElement cur = rules;
        for (String part : path.split("\\.")) {
            if (cur == null || !cur.isJsonObject()) return null;
            cur = cur.getAsJsonObject().get(part);
        }
        return cur == null || cur.isJsonNull() ? null : cur;
    }

    /** Converts JSON to plain Java (Boolean, Double/Long, String, List, Map). */
    public static Object toJava(JsonElement e) {
        if (e == null || e instanceof JsonNull) return null;
        if (e instanceof JsonPrimitive p) {
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                return d == Math.rint(d) && Math.abs(d) < 9e15 ? (Object) (long) d : (Object) d;
            }
            return p.getAsString();
        }
        if (e instanceof JsonArray a) {
            List<Object> out = new ArrayList<>(a.size());
            for (JsonElement x : a) out.add(toJava(x));
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) out.put(en.getKey(), toJava(en.getValue()));
        return out;
    }
}
