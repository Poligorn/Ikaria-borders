package com.poligorn.ikariaborders.map;

import com.poligorn.ikariaborders.zone.Shape;

/**
 * Map-relevant slice of a zone (TZ §10): no rules. Pure JVM, shared by server (encode) and client (draw).
 * {@code shape == null} means a default zone covering the whole dimension.
 */
public record MapZone(String id, String name, String dimension, Shape shape, int priority, int order,
                      int rgb, boolean fill, boolean outline, float opacity, boolean outer, String label) {

    public boolean isDefault() {
        return shape == null;
    }

    /** Point membership in block coordinates; default zones contain everything. */
    public boolean contains(double x, double z) {
        return shape == null || shape.contains(x, z);
    }

    /** Membership of a chunk by its centre (cx*16+8, cz*16+8), same rule as claims (TZ §4.3). */
    public boolean containsChunk(int cx, int cz) {
        return contains(cx * 16 + 8, cz * 16 + 8);
    }

    /** Hover text: {@code map.label} if set, else the zone name (TZ §8.2). */
    public String tooltip() {
        return label != null && !label.isBlank() ? label : name;
    }

    /** Colour as ARGB with alpha = opacity * multiplier; 0 when fully transparent. */
    public int argb(double multiplier) {
        double a = Math.max(0, Math.min(1, opacity * multiplier));
        int alpha = (int) Math.round(a * 255);
        return alpha == 0 ? 0 : (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
