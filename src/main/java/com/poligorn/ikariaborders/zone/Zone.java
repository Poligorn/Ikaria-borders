package com.poligorn.ikariaborders.zone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.poligorn.ikariaborders.api.ZoneView;

import java.util.List;

/** One configured zone. Immutable after {@link ZoneConfig} construction. */
public final class Zone {
    /** Map appearance (TZ §8.2). Not used by 0.1 logic, only reported. */
    public record MapStyle(boolean show, String color, String style, double opacity, String side, String label) {
        public static final MapStyle DEFAULT = new MapStyle(false, null, "outline", 0.6, "inner", null);
    }

    /** Empty zone returned when a dimension has no default zone. */
    public static final Zone NONE = new Zone("none", "Нет зоны", "", null, true, 0, null,
            new JsonObject(), false, MapStyle.DEFAULT, new JsonObject(), -1);

    static {
        NONE.resolvedRules = new JsonObject();
        NONE.parentChain = List.of();
    }

    private final String id;
    private final String name;
    private final String dimension;
    private final Shape shape; // null for default zones
    private final boolean isDefault;
    private final int priority;
    private final String parentId; // null = global
    private final JsonObject ownRules;
    private final boolean mapOnly; // mapOnly: true or rules: false
    private final MapStyle map;
    private final JsonObject meta;
    private final int index; // declaration order in config
    private final Shape.Bounds bounds;

    private volatile JsonObject resolvedRules;
    private volatile List<String> parentChain;
    private volatile ZoneView view;

    public Zone(String id, String name, String dimension, Shape shape, boolean isDefault, int priority,
                String parentId, JsonObject ownRules, boolean mapOnly, MapStyle map, JsonObject meta, int index) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.shape = shape;
        this.isDefault = isDefault;
        this.priority = priority;
        this.parentId = parentId;
        this.ownRules = ownRules;
        this.mapOnly = mapOnly;
        this.map = map;
        this.meta = meta;
        this.index = index;
        this.bounds = shape == null ? null : shape.bounds();
    }

    /** Called once by ZoneConfig. */
    void resolve(JsonObject resolvedRules, List<String> parentChain) {
        this.resolvedRules = resolvedRules;
        this.parentChain = List.copyOf(parentChain);
    }

    /** Whether this zone is eligible for "current zone" lookups (not default, not map-only). */
    boolean isLookupCandidate() {
        return !isDefault && !mapOnly && shape != null;
    }

    public boolean contains(double x, double z) {
        if (isDefault) return true;
        if (shape == null || !bounds.contains(x, z)) return false;
        return shape.contains(x, z);
    }

    public ZoneView view() {
        ZoneView v = view;
        if (v == null) view = v = new ZoneView(this);
        return v;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public Shape shape() { return shape; }
    public boolean isDefault() { return isDefault; }
    public int priority() { return priority; }
    public String parentId() { return parentId; }
    public JsonObject ownRules() { return ownRules; }
    public boolean mapOnly() { return mapOnly; }
    public MapStyle map() { return map; }
    public JsonObject meta() { return meta; }
    public int index() { return index; }
    public Shape.Bounds bounds() { return bounds; }
    /** Rules after inheritance: global, then parents (root first), then this zone. */
    public JsonObject resolvedRules() { return resolvedRules; }
    /** Ids of parents, nearest first (excluding "global"). */
    public List<String> parentChain() { return parentChain; }

    public JsonElement rule(String path) {
        return Rules.lookup(resolvedRules, path);
    }

    /** Boolean rule or {@code dflt} if unset / not a boolean. */
    public boolean ruleBool(String path, boolean dflt) {
        JsonElement e = rule(path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : dflt;
    }

    /** Claims are allowed unless {@code claims.allow} is explicitly false (vanilla otherwise). */
    public boolean claimsAllowed() {
        return ruleBool("claims.allow", true);
    }

    @Override public String toString() {
        return "Zone[" + id + "]";
    }
}
