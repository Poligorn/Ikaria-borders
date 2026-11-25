package com.poligorn.ikariaborders.script;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Key/value data per zone (TZ §21.5), at most {@link #MAX_BYTES} of serialized JSON per zone.
 * Pure JVM; persistence is done by the SavedData wrapper through {@link #toJson()} / {@link #load(String)}.
 */
public final class ZoneDataStore {
    public static final int MAX_BYTES = 16 * 1024;
    private static final Gson GSON = new Gson();

    private final Map<String, JsonObject> data = new HashMap<>();
    private Runnable onChange = () -> {};

    public void onChange(Runnable r) { this.onChange = r; }

    /** Serialized size of a zone's data in bytes (UTF-8). */
    public synchronized int sizeBytes(String zoneId) {
        JsonObject o = data.get(zoneId);
        return o == null ? 0 : GSON.toJson(o).getBytes(StandardCharsets.UTF_8).length;
    }

    /** Stores a value. Returns false (and changes nothing) if the zone would exceed 16 KiB. */
    public synchronized boolean set(String zoneId, String key, JsonElement value) {
        JsonObject cur = data.get(zoneId);
        JsonObject next = cur == null ? new JsonObject() : cur.deepCopy();
        next.add(key, value.deepCopy());
        if (GSON.toJson(next).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return false;
        data.put(zoneId, next);
        onChange.run();
        return true;
    }

    public synchronized JsonElement get(String zoneId, String key) {
        JsonObject o = data.get(zoneId);
        return o == null ? null : o.get(key);
    }

    public synchronized boolean has(String zoneId, String key) {
        return get(zoneId, key) != null;
    }

    public synchronized boolean remove(String zoneId, String key) {
        JsonObject o = data.get(zoneId);
        if (o == null || o.remove(key) == null) return false;
        if (o.size() == 0) data.remove(zoneId);
        onChange.run();
        return true;
    }

    public synchronized List<String> keys(String zoneId) {
        JsonObject o = data.get(zoneId);
        return o == null ? List.of() : new ArrayList<>(o.keySet());
    }

    public synchronized void clear(String zoneId) {
        if (data.remove(zoneId) != null) onChange.run();
    }

    public synchronized String toJson() {
        JsonObject root = new JsonObject();
        data.forEach(root::add);
        return GSON.toJson(root);
    }

    public synchronized void load(String json) {
        data.clear();
        if (json == null || json.isBlank()) return;
        JsonElement e = JsonParser.parseString(json);
        if (!e.isJsonObject()) return;
        for (var en : e.getAsJsonObject().entrySet()) {
            if (en.getValue().isJsonObject()) data.put(en.getKey(), en.getValue().getAsJsonObject());
        }
    }
}
