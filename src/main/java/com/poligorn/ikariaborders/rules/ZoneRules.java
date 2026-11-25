package com.poligorn.ikariaborders.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed enforcement rules (TZ §6) of a zone, built once from its resolved (inherited) rule tree.
 * {@code null} Boolean = unset = vanilla. Pure JVM.
 */
public final class ZoneRules {
    /** Default {@code spawn.reasons}: natural-style spawns only; spawners, eggs and commands stay untouched. */
    public static final Set<String> DEFAULT_SPAWN_REASONS =
            Set.of("NATURAL", "CHUNK_GENERATION", "PATROL", "REINFORCEMENT", "EVENT");

    public static final int DEFAULT_PLAYER_TICK = 20, MIN_PLAYER_TICK = 5;
    public static final int DEFAULT_ZONE_TICK = 100, MIN_ZONE_TICK = 20;

    public static final ZoneRules EMPTY = of(new JsonObject());

    public final Boolean pvp;
    public final String pvpCheck; // "both" | "victim"
    public final boolean pvpPets;
    public final List<String> damageDeny;       // ids and #tags as written
    public final boolean damageAllTargets;      // damage.targets == "all"
    public final Boolean explosionsBlockDamage;
    public final Boolean explosionsEntityDamage;
    public final Map<String, Boolean> spawnCategories; // lower-case category name
    public final Set<String> spawnReasons;      // upper-case MobSpawnType names
    public final List<String> denyEntities;
    public final List<String> allowEntities;
    public final boolean removeHostileOnEnter;
    public final int playerTickInterval;
    public final int zoneTickInterval;

    private ZoneRules(JsonObject r) {
        pvp = tri(r, "pvp");
        pvpCheck = "victim".equals(str(r, "pvpCheck")) ? "victim" : "both";
        pvpPets = Boolean.TRUE.equals(tri(r, "pvpPets"));
        JsonObject damage = obj(r, "damage");
        damageDeny = strings(damage == null ? null : damage.get("deny"));
        damageAllTargets = damage != null && "all".equals(str(damage, "targets"));
        JsonObject ex = obj(r, "explosions");
        explosionsBlockDamage = ex == null ? null : tri(ex, "blockDamage");
        explosionsEntityDamage = ex == null ? null : tri(ex, "entityDamage");
        JsonObject spawn = obj(r, "spawn");
        Map<String, Boolean> cats = new LinkedHashMap<>();
        JsonObject catObj = spawn == null ? null : obj(spawn, "categories");
        if (catObj != null) {
            for (var e : catObj.entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isBoolean()) {
                    cats.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue().getAsBoolean());
                }
            }
        }
        spawnCategories = Map.copyOf(cats);
        List<String> reasons = strings(spawn == null ? null : spawn.get("reasons"));
        spawnReasons = spawn != null && spawn.has("reasons")
                ? Set.copyOf(reasons.stream().map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList())
                : DEFAULT_SPAWN_REASONS;
        denyEntities = strings(spawn == null ? null : spawn.get("denyEntities"));
        allowEntities = strings(spawn == null ? null : spawn.get("allowEntities"));
        JsonObject mobs = obj(r, "mobs");
        removeHostileOnEnter = mobs != null && Boolean.TRUE.equals(tri(mobs, "removeHostileOnEnter"));
        JsonObject scripting = obj(r, "scripting");
        playerTickInterval = Math.max(MIN_PLAYER_TICK, intOr(scripting, "playerTickInterval", DEFAULT_PLAYER_TICK));
        zoneTickInterval = Math.max(MIN_ZONE_TICK, intOr(scripting, "zoneTickInterval", DEFAULT_ZONE_TICK));
    }

    public static ZoneRules of(JsonObject resolved) {
        return new ZoneRules(resolved);
    }

    public boolean pvpForbidden() { return Boolean.FALSE.equals(pvp); }
    public boolean hasDamageRules() { return !damageDeny.isEmpty(); }
    public boolean hasExplosionRules() { return Boolean.FALSE.equals(explosionsBlockDamage) || Boolean.FALSE.equals(explosionsEntityDamage); }

    public boolean hasSpawnRules() {
        return spawnCategories.containsValue(Boolean.FALSE) || !denyEntities.isEmpty();
    }

    // ---- JSON helpers ----

    private static Boolean tri(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : null;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static int intOr(JsonObject o, String k, int dflt) {
        JsonElement e = o == null ? null : o.get(k);
        if (e instanceof JsonPrimitive p && p.isNumber()) {
            double d = p.getAsDouble();
            if (!Double.isNaN(d) && !Double.isInfinite(d)) return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, d));
        }
        return dflt;
    }

    private static List<String> strings(JsonElement e) {
        if (!(e instanceof JsonArray a)) return List.of();
        List<String> out = new ArrayList<>(a.size());
        for (JsonElement x : a) if (x.isJsonPrimitive() && x.getAsJsonPrimitive().isString()) out.add(x.getAsString());
        return List.copyOf(out);
    }
}
