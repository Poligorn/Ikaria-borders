package com.poligorn.ikariaborders.zone;

import java.util.List;
import java.util.Locale;

/**
 * Zone shape in block X/Z coordinates (Y is ignored). Pure JVM, no Minecraft classes.
 * Boundary conventions (TZ §5.2):
 * circle: dist &lt; radius; square: max(|dx|,|dz|) &lt; half (boundary is OUTSIDE);
 * rect: inclusive by block; polygon: even-odd, edge counts as inside.
 */
public sealed interface Shape {

    boolean contains(double x, double z);

    /** Conservative bounding box (inclusive), used for fast rejection. */
    Bounds bounds();

    /** Short human-readable description (Russian), for commands. */
    String describe();

    record Bounds(double minX, double maxX, double minZ, double maxZ) {
        public boolean contains(double x, double z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean intersects(Bounds o) {
            return minX <= o.maxX && o.minX <= maxX && minZ <= o.maxZ && o.minZ <= maxZ;
        }

        public Bounds union(Bounds o) {
            return new Bounds(Math.min(minX, o.minX), Math.max(maxX, o.maxX),
                    Math.min(minZ, o.minZ), Math.max(maxZ, o.maxZ));
        }
    }

    record Circle(double cx, double cz, double radius) implements Shape {
        @Override public boolean contains(double x, double z) {
            double dx = x - cx, dz = z - cz;
            return dx * dx + dz * dz < radius * radius;
        }
        @Override public Bounds bounds() {
            return new Bounds(cx - radius, cx + radius, cz - radius, cz + radius);
        }
        @Override public String describe() {
            return "круг (" + n(cx) + ", " + n(cz) + ") r=" + n(radius);
        }
    }

    record Square(double cx, double cz, double half) implements Shape {
        @Override public boolean contains(double x, double z) {
            return Math.max(Math.abs(x - cx), Math.abs(z - cz)) < half;
        }
        @Override public Bounds bounds() {
            return new Bounds(cx - half, cx + half, cz - half, cz + half);
        }
        @Override public String describe() {
            return "квадрат (" + n(cx) + ", " + n(cz) + ") half=" + n(half);
        }
    }

    /** Inclusive by whole blocks: a point belongs to block floor(x), floor(z). */
    record Rect(int minX, int minZ, int maxX, int maxZ) implements Shape {
        public static Rect of(double x1, double z1, double x2, double z2) {
            return new Rect((int) Math.floor(Math.min(x1, x2)), (int) Math.floor(Math.min(z1, z2)),
                    (int) Math.floor(Math.max(x1, x2)), (int) Math.floor(Math.max(z1, z2)));
        }
        @Override public boolean contains(double x, double z) {
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
        }
        @Override public Bounds bounds() {
            return new Bounds(minX, maxX + 1.0, minZ, maxZ + 1.0);
        }
        @Override public String describe() {
            return "прямоугольник (" + minX + ", " + minZ + ")–(" + maxX + ", " + maxZ + ")";
        }
    }

    record Polygon(double[] xs, double[] zs) implements Shape {
        private static final double EPS = 1e-9;

        public Polygon {
            if (xs.length != zs.length || xs.length < 3) {
                throw new IllegalArgumentException("polygon needs >= 3 points");
            }
        }

        public int size() {
            return xs.length;
        }

        @Override public boolean contains(double x, double z) {
            int n = xs.length;
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                if (onSegment(xs[j], zs[j], xs[i], zs[i], x, z)) return true;
                if ((zs[i] > z) != (zs[j] > z)
                        && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                    inside = !inside;
                }
            }
            return inside;
        }

        @Override public Bounds bounds() {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (int i = 0; i < xs.length; i++) {
                minX = Math.min(minX, xs[i]);
                maxX = Math.max(maxX, xs[i]);
                minZ = Math.min(minZ, zs[i]);
                maxZ = Math.max(maxZ, zs[i]);
            }
            return new Bounds(minX, maxX, minZ, maxZ);
        }

        /** True if any two non-adjacent edges intersect (TZ: self-intersection is only a warning). */
        public boolean selfIntersects() {
            int n = xs.length;
            for (int i = 0; i < n; i++) {
                int i2 = (i + 1) % n;
                for (int j = i + 1; j < n; j++) {
                    int j2 = (j + 1) % n;
                    if (j == i + 1 || j2 == i) continue; // adjacent edges share a vertex
                    if (segmentsIntersect(xs[i], zs[i], xs[i2], zs[i2], xs[j], zs[j], xs[j2], zs[j2])) return true;
                }
            }
            return false;
        }

        @Override public String describe() {
            return "многоугольник (" + xs.length + " точек)";
        }

        private static boolean onSegment(double ax, double az, double bx, double bz, double px, double pz) {
            double cross = (bx - ax) * (pz - az) - (bz - az) * (px - ax);
            double len = Math.hypot(bx - ax, bz - az);
            if (Math.abs(cross) > EPS * Math.max(1.0, len)) return false;
            return px >= Math.min(ax, bx) - EPS && px <= Math.max(ax, bx) + EPS
                    && pz >= Math.min(az, bz) - EPS && pz <= Math.max(az, bz) + EPS;
        }

        private static double orient(double ax, double az, double bx, double bz, double cx, double cz) {
            return (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
        }

        private static boolean segmentsIntersect(double ax, double az, double bx, double bz,
                                                 double cx, double cz, double dx, double dz) {
            double o1 = orient(ax, az, bx, bz, cx, cz), o2 = orient(ax, az, bx, bz, dx, dz);
            double o3 = orient(cx, cz, dx, dz, ax, az), o4 = orient(cx, cz, dx, dz, bx, bz);
            if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0)) && ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) return true;
            return (o1 == 0 && onSegment(ax, az, bx, bz, cx, cz))
                    || (o2 == 0 && onSegment(ax, az, bx, bz, dx, dz))
                    || (o3 == 0 && onSegment(cx, cz, dx, dz, ax, az))
                    || (o4 == 0 && onSegment(cx, cz, dx, dz, bx, bz));
        }
    }

    record Union(List<Shape> shapes) implements Shape {
        public Union {
            shapes = List.copyOf(shapes);
            if (shapes.isEmpty()) throw new IllegalArgumentException("union needs shapes");
        }
        @Override public boolean contains(double x, double z) {
            for (Shape s : shapes) if (s.contains(x, z)) return true;
            return false;
        }
        @Override public Bounds bounds() {
            Bounds b = shapes.get(0).bounds();
            for (int i = 1; i < shapes.size(); i++) b = b.union(shapes.get(i).bounds());
            return b;
        }
        @Override public String describe() {
            return "объединение (" + shapes.size() + ")";
        }
    }

    record Difference(Shape base, List<Shape> minus) implements Shape {
        public Difference {
            minus = List.copyOf(minus);
        }
        @Override public boolean contains(double x, double z) {
            if (!base.contains(x, z)) return false;
            for (Shape s : minus) if (s.contains(x, z)) return false;
            return true;
        }
        @Override public Bounds bounds() {
            return base.bounds();
        }
        @Override public String describe() {
            return "разность (" + base.describe() + " − " + minus.size() + ")";
        }
    }

    private static String n(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
