package com.poligorn.ikariaborders.api;

import com.google.gson.JsonElement;
import com.poligorn.ikariaborders.zone.Rules;
import com.poligorn.ikariaborders.zone.Zone;

/**
 * Read-only view of a zone for other mods / KubeJS. Never null from the API:
 * a dimension without a default zone yields {@link #NONE} (id {@code "none"}).
 */
public final class ZoneView {
    public static final ZoneView NONE = Zone.NONE.view();

    private final Zone zone;

    /** Use {@link Zone#view()}; public only because Zone lives in another package. */
    public ZoneView(Zone zone) {
        this.zone = zone;
    }

    public String id() { return zone.id(); }
    public String name() { return zone.name(); }
    public int priority() { return zone.priority(); }
    public String dimension() { return zone.dimension(); }
    public boolean isNone() { return zone == Zone.NONE; }

    /** Map colour "#RRGGBB" or null if not set. */
    public String color() { return zone.map().color(); }

    /** Parent zone id, or null (parent is the global defaults). */
    public String parentId() { return zone.parentId(); }

    /** Meta value as a string (primitives as-is, structures as JSON), or null. */
    public String meta(String key) {
        JsonElement e = zone.meta().get(key);
        if (e == null || e.isJsonNull()) return null;
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    /**
     * Resolved (inherited) rule by dotted path, e.g. {@code "pvp"} or {@code "claims.allow"}.
     * Returns Boolean / Long / Double / String / List / Map, or null if unset (vanilla behaviour).
     */
    public Object rule(String path) { return Rules.toJava(zone.rule(path)); }

    public boolean ruleBool(String path, boolean dflt) { return zone.ruleBool(path, dflt); }

    /** Internal: the underlying zone. Not a stable API. */
    public Zone zone() { return zone; }

    @Override public boolean equals(Object o) {
        return o instanceof ZoneView v && v.zone == zone;
    }

    @Override public int hashCode() { return System.identityHashCode(zone); }

    @Override public String toString() { return "ZoneView[" + zone.id() + "]"; }
}
