package com.poligorn.ikariaborders.zone;

/** Distance / depth queries (TZ §21.3). Pure JVM. */
public final class ZoneMetrics {
    private ZoneMetrics() {}

    /**
     * Distance from (x,z) to the zone border in blocks: positive inside, negative outside.
     * A default zone has no shape of its own: its "border" is the border of the higher-priority zones of the
     * dimension, so the value is the negated signed distance to their union (positive = far from all of them).
     * Returns {@code +Infinity} if a default zone has no higher-priority zones at all.
     */
    public static double distanceToBorder(ZoneConfig cfg, Zone zone, double x, double z) {
        if (zone.shape() != null && !zone.isDefault()) return zone.shape().signedDistance(x, z);
        if (!zone.isDefault()) return Double.NEGATIVE_INFINITY; // Zone.NONE
        double maxInside = Double.NEGATIVE_INFINITY;
        for (Zone o : cfg.zones(zone.dimension())) {
            if (o.isLookupCandidate() && o.priority() > zone.priority()) {
                maxInside = Math.max(maxInside, o.shape().signedDistance(x, z));
            }
        }
        return -maxInside; // no higher zones: +Infinity
    }

    /**
     * "Depth": how far the point is inside the zone from its edge (0 if outside or if the zone has no edge to
     * measure from). For a default zone, the distance to the nearest border of a higher-priority zone.
     */
    public static double depth(ZoneConfig cfg, Zone zone, double x, double z) {
        double d = distanceToBorder(cfg, zone, x, z);
        return Double.isInfinite(d) || d < 0 ? 0 : d;
    }
}
