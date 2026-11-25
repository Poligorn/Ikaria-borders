package com.poligorn.ikariaborders.script;

import com.google.gson.JsonElement;
import com.poligorn.ikariaborders.zone.Rules;

import java.util.List;

/**
 * {@code zone.data} (persistent, SavedData) and {@code zone.temp} (until restart) as seen from scripts.
 * Values: strings, numbers, booleans and small nested objects/arrays; at most 16 KiB per zone.
 * Nested values come back as Java Map / List.
 */
public final class ZoneDataJS {
    private final String zoneId;
    private final boolean persistent;

    ZoneDataJS(String zoneId, boolean persistent) {
        this.zoneId = zoneId;
        this.persistent = persistent;
    }

    private ZoneDataStore store() {
        ScriptRuntime.requireServerThread("zone.data");
        return persistent ? ScriptRuntime.persistentData() : ScriptRuntime.tempData();
    }

    /** Value or null if the key is not set. */
    public Object get(String key) {
        return Rules.toJava(store().get(zoneId, key));
    }

    public Object get(String key, Object dflt) {
        Object v = get(key);
        return v == null ? dflt : v;
    }

    public boolean has(String key) {
        return store().has(zoneId, key);
    }

    /** @return false (and a line in the log) if the value is unsupported or the zone would exceed 16 KiB */
    public boolean set(String key, Object value) {
        ZoneDataStore s = store();
        try {
            JsonElement json = JsonConv.toJson(value);
            if (s.set(zoneId, key, json)) return true;
            ScriptRuntime.warnThrottled("data-limit:" + zoneId,
                    "zone.data(" + zoneId + "): превышен лимит " + ZoneDataStore.MAX_BYTES + " байт, значение '" + key + "' не записано");
        } catch (IllegalArgumentException e) {
            ScriptRuntime.warnThrottled("data-bad:" + zoneId + ":" + key,
                    "zone.data(" + zoneId + ") '" + key + "': " + e.getMessage());
        }
        return false;
    }

    public boolean remove(String key) {
        return store().remove(zoneId, key);
    }

    public List<String> keys() {
        return store().keys(zoneId);
    }

    public void clear() {
        store().clear(zoneId);
    }

    /** Serialized size in bytes (limit 16384). */
    public int sizeBytes() {
        return store().sizeBytes(zoneId);
    }

    @Override public String toString() {
        return (persistent ? "ZoneData[" : "ZoneTemp[") + zoneId + "]";
    }
}
