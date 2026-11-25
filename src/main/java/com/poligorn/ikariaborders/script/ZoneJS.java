package com.poligorn.ikariaborders.script;

import com.google.gson.JsonElement;
import com.poligorn.ikariaborders.PlayerZoneTracker;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.zone.Rules;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneMetrics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Zone as seen from scripts (bean getters, so {@code zone.id}, {@code zone.name}, {@code zone.data} work in Rhino).
 * No KubeJS types here. Read-only in 0.3: no runtime setRule / map setters.
 */
public final class ZoneJS {
    private final Zone zone;

    private ZoneJS(Zone zone) {
        this.zone = zone;
    }

    public static ZoneJS of(Zone zone) {
        return new ZoneJS(zone);
    }

    public String getId() { return zone.id(); }
    public String getName() { return zone.name(); }
    public int getPriority() { return zone.priority(); }
    public String getDimension() { return zone.dimension(); }
    /** Parent zone id or null. */
    public String getParent() { return zone.parentId(); }
    public boolean isDefault() { return zone.isDefault(); }
    public String getColor() { return zone.map().color(); }

    /** Persistent key/value data (16 KiB per zone). */
    public ZoneDataJS getData() { return new ZoneDataJS(zone.id(), true); }

    /** Temporary key/value data, kept until restart. */
    public ZoneDataJS getTemp() { return new ZoneDataJS(zone.id(), false); }

    /**
     * Custom flag ({@code rules.custom}, inherited like rules): the configured value, else the default from
     * {@code registerFlag}, else null.
     */
    public Object flag(String name) {
        JsonElement v = zone.customFlag(name);
        return v != null ? Rules.toJava(v) : FlagRegistry.defaultOf(name);
    }

    /** Resolved (inherited) rule by dotted path, or null. */
    public Object rule(String path) {
        return Rules.toJava(zone.rule(path));
    }

    public String meta(String key) {
        return zone.view().meta(key);
    }

    /** Point is inside this zone's own shape (priorities ignored). */
    public boolean isInside(Level level, double x, double z) {
        return zone.dimension().equals(level.dimension().location().toString()) && zone.contains(x, z);
    }

    /** Distance to the border: positive inside, negative outside. */
    public double distanceToBorder(double x, double z) {
        ScriptRuntime.requireServerThread("zone.distanceToBorder");
        return ZoneMetrics.distanceToBorder(ZoneManager.config(), zone, x, z);
    }

    public double depth(double x, double z) {
        ScriptRuntime.requireServerThread("zone.depth");
        return ZoneMetrics.depth(ZoneManager.config(), zone, x, z);
    }

    /** Players currently in the zone (tracker cache). */
    public List<ServerPlayer> getPlayers() {
        ScriptRuntime.requireServerThread("zone.players");
        return PlayerZoneTracker.playersIn(ScriptRuntime.server(), zone.id());
    }

    @Override public boolean equals(Object o) { return o instanceof ZoneJS z && z.zone == zone; }
    @Override public int hashCode() { return System.identityHashCode(zone); }
    @Override public String toString() { return "Zone[" + zone.id() + "]"; }
}
