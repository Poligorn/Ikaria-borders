package com.poligorn.ikariaborders.map;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Client display settings (ikaria_borders-client.toml + runtime toggle). Pure JVM. */
public record MapSettings(boolean enabled, boolean worldMap, boolean minimap, double opacityMultiplier,
                          Set<String> hiddenZones, int outlineWidth, int hatchStep, int hatchWidth, double hatchOpacity) {
    public static final int DEF_OUTLINE_WIDTH = 2, DEF_HATCH_STEP = 4, DEF_HATCH_WIDTH = 1;
    public static final double DEF_HATCH_OPACITY = 0.7;
    public static final MapSettings DEFAULT = new MapSettings(true, true, true, 1.0, Set.of());

    /** Defaults for outline width / hatch (not in TZ §11). */
    public MapSettings(boolean enabled, boolean worldMap, boolean minimap, double opacityMultiplier, Set<String> hiddenZones) {
        this(enabled, worldMap, minimap, opacityMultiplier, hiddenZones,
                DEF_OUTLINE_WIDTH, DEF_HATCH_STEP, DEF_HATCH_WIDTH, DEF_HATCH_OPACITY);
    }

    public MapSettings {
        hiddenZones = Set.copyOf(hiddenZones);
        if (Double.isNaN(opacityMultiplier) || opacityMultiplier < 0) opacityMultiplier = 0;
        if (opacityMultiplier > 2) opacityMultiplier = 2;
        outlineWidth = Math.max(1, Math.min(8, outlineWidth));
        hatchStep = Math.max(2, Math.min(16, hatchStep));
        hatchWidth = Math.max(1, Math.min(hatchStep, hatchWidth));
        if (Double.isNaN(hatchOpacity) || hatchOpacity < 0) hatchOpacity = 0;
        if (hatchOpacity > 1) hatchOpacity = 1;
    }

    public boolean active(MapTarget t) {
        return enabled && (t == MapTarget.WORLD_MAP ? worldMap : minimap);
    }

    public MapSettings withEnabled(boolean e) {
        return new MapSettings(e, worldMap, minimap, opacityMultiplier, hiddenZones,
                outlineWidth, hatchStep, hatchWidth, hatchOpacity);
    }

    /** Hash of everything that changes how a map is painted for the given target. */
    public int hash(MapTarget t) {
        return Objects.hash(active(t), opacityMultiplier, new TreeSet<>(hiddenZones),
                outlineWidth, hatchStep, hatchWidth, hatchOpacity);
    }
}
