package com.poligorn.ikariaborders.map;

import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server tells clients about zones (TZ §10): only zones with {@code map.show = true}
 * (map-only ones included, default zones with show=false are NOT sent so they never fill a dimension).
 * {@code version} is a content hash of the map-relevant data only: rule-only reloads do not repaint maps.
 */
public record ZoneMapData(long version, List<MapZone> zones) {
    public static final ZoneMapData EMPTY = new ZoneMapData(0, List.of());

    public ZoneMapData {
        zones = List.copyOf(zones);
    }

    /** Server side: derive the map slice from a validated config. */
    public static ZoneMapData of(ZoneConfig config) {
        List<MapZone> out = new ArrayList<>();
        for (Zone z : config.allZones()) {
            Zone.MapStyle m = z.map();
            if (!m.show()) continue;
            if (z.shape() == null && !z.isDefault()) continue;
            String style = m.style() == null ? "outline" : m.style();
            out.add(new MapZone(z.id(), z.name(), z.dimension(), z.isDefault() ? null : z.shape(), z.priority(),
                    z.index(), parseRgb(m.color()), style.contains("fill"), style.contains("outline"),
                    (float) m.opacity(), "outer".equals(m.side()), m.label(), z.mapOnly()));
        }
        return ZoneMapCodec.withComputedVersion(out);
    }

    /** "#RRGGBB" to int; null / malformed gives white. */
    static int parseRgb(String color) {
        if (color == null || color.length() != 7 || color.charAt(0) != '#') return 0xFFFFFF;
        try {
            return Integer.parseInt(color.substring(1), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
