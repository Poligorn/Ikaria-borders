package com.poligorn.ikariaborders.map;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Client display settings (ikaria_borders-client.toml + runtime toggle). Pure JVM. */
public record MapSettings(boolean enabled, boolean worldMap, boolean minimap, double opacityMultiplier,
                          Set<String> hiddenZones) {
    public static final MapSettings DEFAULT = new MapSettings(true, true, true, 1.0, Set.of());

    public MapSettings {
        hiddenZones = Set.copyOf(hiddenZones);
        if (Double.isNaN(opacityMultiplier) || opacityMultiplier < 0) opacityMultiplier = 0;
        if (opacityMultiplier > 2) opacityMultiplier = 2;
    }

    public boolean active(MapTarget t) {
        return enabled && (t == MapTarget.WORLD_MAP ? worldMap : minimap);
    }

    public MapSettings withEnabled(boolean e) {
        return new MapSettings(e, worldMap, minimap, opacityMultiplier, hiddenZones);
    }

    /** Hash of everything that changes how a map is painted for the given target. */
    public int hash(MapTarget t) {
        return Objects.hash(active(t), opacityMultiplier, new TreeSet<>(hiddenZones));
    }
}
