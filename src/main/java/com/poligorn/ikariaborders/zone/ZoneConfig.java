package com.poligorn.ikariaborders.zone;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable validated set of zones. Swapped atomically by reference. */
public final class ZoneConfig {
    public static final ZoneConfig EMPTY = new ZoneConfig(new JsonObject(), List.of());

    private final JsonObject globalRules;
    private final List<Zone> zones;
    private final Map<String, Zone> byId;
    private final Map<String, List<Zone>> byDimension; // declaration order
    private final Map<String, Zone[]> lookupOrder; // priority desc, then declaration order
    private final Map<String, Zone> defaults;

    /** Zones must have unique ids and acyclic, existing parents (the loader guarantees this). */
    public ZoneConfig(JsonObject globalRules, List<Zone> zones) {
        this.globalRules = globalRules;
        this.zones = List.copyOf(zones);
        Map<String, Zone> ids = new LinkedHashMap<>();
        Map<String, List<Zone>> dims = new LinkedHashMap<>();
        Map<String, List<Zone>> cands = new HashMap<>();
        Map<String, Zone> defs = new HashMap<>();
        for (Zone z : zones) {
            ids.put(z.id(), z);
            dims.computeIfAbsent(z.dimension(), k -> new ArrayList<>()).add(z);
            if (z.isDefault()) defs.putIfAbsent(z.dimension(), z);
            else if (z.isLookupCandidate()) cands.computeIfAbsent(z.dimension(), k -> new ArrayList<>()).add(z);
        }
        this.byId = Collections.unmodifiableMap(ids);
        Map<String, List<Zone>> dimsFinal = new LinkedHashMap<>();
        dims.forEach((k, v) -> dimsFinal.put(k, List.copyOf(v)));
        this.byDimension = Collections.unmodifiableMap(dimsFinal);
        this.defaults = Map.copyOf(defs);
        Map<String, Zone[]> order = new HashMap<>();
        Comparator<Zone> cmp = Comparator.comparingInt(Zone::priority).reversed().thenComparingInt(Zone::index);
        cands.forEach((k, v) -> {
            v.sort(cmp);
            order.put(k, v.toArray(new Zone[0]));
        });
        this.lookupOrder = order;
        for (Zone z : zones) z.resolve(resolveRules(z), chainOf(z));
    }

    private List<String> chainOf(Zone z) {
        List<String> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(z.id());
        String p = z.parentId();
        while (p != null && seen.add(p)) {
            chain.add(p);
            Zone pz = byId.get(p);
            p = pz == null ? null : pz.parentId();
        }
        return chain;
    }

    private JsonObject resolveRules(Zone z) {
        if (z.mapOnly()) return new JsonObject(); // map-only zones carry no rules
        List<Zone> chain = new ArrayList<>();
        chain.add(z);
        for (String p : chainOf(z)) {
            Zone pz = byId.get(p);
            if (pz != null) chain.add(pz);
        }
        JsonObject out = globalRules.deepCopy();
        for (int i = chain.size() - 1; i >= 0; i--) out = Rules.merge(out, chain.get(i).ownRules());
        return out;
    }

    /** Current zone at exact coordinates; never null. Map-only zones are skipped. */
    public Zone zoneAt(String dimension, double x, double z) {
        Zone[] order = lookupOrder.get(dimension);
        if (order != null) {
            for (Zone zone : order) if (zone.contains(x, z)) return zone;
        }
        Zone def = defaults.get(dimension);
        return def != null ? def : Zone.NONE;
    }

    /** Zone of a chunk by its center (cx*16+8, cz*16+8), same as claims and map use (TZ §4.3). */
    public Zone zoneOfChunk(String dimension, int cx, int cz) {
        return zoneAt(dimension, cx * 16 + 8, cz * 16 + 8);
    }

    public Zone zone(String id) {
        return byId.get(id);
    }

    /** All zones of a dimension in declaration order (includes default and map-only). */
    public List<Zone> zones(String dimension) {
        return byDimension.getOrDefault(dimension, List.of());
    }

    public List<Zone> allZones() {
        return zones;
    }

    public Set<String> dimensions() {
        return byDimension.keySet();
    }

    public Zone defaultZone(String dimension) {
        return defaults.get(dimension);
    }

    public JsonObject globalRules() {
        return globalRules;
    }
}
