package com.poligorn.ikariaborders.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client-side chunk painter (TZ §11). Pure JVM; the Xaero adapters only translate calls.
 *
 * <p>Semantics (per dimension, among visible zones = {@code map.show} and not in {@code hiddenZones}):
 * <ul>
 *   <li><b>Zone of a chunk</b> = highest priority zone containing the chunk centre (cx*16+8, cz*16+8); ties: declared first.
 *       Fill colour comes from that zone if its style has {@code fill}.</li>
 *   <li><b>Side colours</b>: for each of N/E/S/W the highest-priority zone with {@code outline} for which this side is
 *       a border of <i>that zone's own shape</i> (side {@code inner}: chunk inside, neighbour outside;
 *       {@code outer}: chunk outside, neighbour inside). So a map-only zone nested under others only outlines its own edge.</li>
 *   <li>Colour layout of {@code getColors}: [0]=centre, [1]=north (-Z), [2]=east (+X), [3]=south (+Z), [4]=west (-X),
 *       derived from the bytecode of Xaero's {@code ChunkHighlighter.getChunkHighlitColor}; a side equal to the centre means "no border".</li>
 * </ul>
 * Regions of 32x32 chunks are computed once and kept in an LRU (256). Thread-safe (coarse lock).
 */
public final class ZoneMapModel {
    public static final int REGION_CHUNKS = 32;
    public static final int REGION_BLOCKS = REGION_CHUNKS * 16;
    public static final int MAX_REGIONS = 256;
    private static final int GRID = REGION_CHUNKS + 2; // one chunk margin for neighbour checks

    private volatile ZoneMapData data = ZoneMapData.EMPTY;
    private volatile MapSettings settings = MapSettings.DEFAULT;
    private volatile Map<String, DimView> dims = Map.of();
    private int computations; // test hook

    private final Map<RegionKey, Region> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<RegionKey, Region> e) {
            return size() > MAX_REGIONS;
        }
    };

    private record RegionKey(String dim, int rx, int rz) {}

    private static final class DimView {
        final MapZone[] zones;     // visible, priority desc then declaration order
        final double[][] bounds;   // per zone [minX,maxX,minZ,maxZ]; null for default zones
        DimView(MapZone[] zones) {
            this.zones = zones;
            this.bounds = new double[zones.length][];
            for (int i = 0; i < zones.length; i++) {
                if (zones[i].shape() != null) {
                    var b = zones[i].shape().bounds();
                    bounds[i] = new double[]{b.minX(), b.maxX(), b.minZ(), b.maxZ()};
                }
            }
        }
    }

    private static final class Region {
        final int[] colors = new int[5 * REGION_CHUNKS * REGION_CHUNKS];
        final byte[] sides = new byte[REGION_CHUNKS * REGION_CHUNKS]; // ChunkHighlightRaster bits
        final MapZone[] tip = new MapZone[REGION_CHUNKS * REGION_CHUNKS];
        boolean any;
    }

    public synchronized void setData(ZoneMapData d) {
        this.data = Objects.requireNonNull(d);
        rebuild();
    }

    public synchronized void setSettings(MapSettings s) {
        this.settings = Objects.requireNonNull(s);
        rebuild();
    }

    public MapSettings settings() { return settings; }
    public ZoneMapData data() { return data; }
    public long dataVersion() { return data.version(); }
    public synchronized int computations() { return computations; }
    public synchronized int cachedRegions() { return cache.size(); }

    private void rebuild() {
        cache.clear();
        Map<String, List<MapZone>> byDim = new HashMap<>();
        for (MapZone z : data.zones()) {
            if (settings.hiddenZones().contains(z.id())) continue;
            if (z.isDefault() && !z.fill()) continue; // a default zone has no border of its own; outline-only draws nothing
            byDim.computeIfAbsent(z.dimension(), k -> new ArrayList<>()).add(z);
        }
        Comparator<MapZone> cmp = Comparator.comparingInt(MapZone::priority).reversed().thenComparingInt(MapZone::order);
        Map<String, DimView> out = new HashMap<>();
        byDim.forEach((k, v) -> {
            v.sort(cmp);
            out.put(k, new DimView(v.toArray(new MapZone[0])));
        });
        dims = out;
    }

    public boolean active(MapTarget t) {
        return settings.active(t);
    }

    /** Value for Xaero's {@code calculateRegionHash}: changes with the config version and client settings. */
    public int regionHash(MapTarget t) {
        return Objects.hash(data.version(), settings.hash(t));
    }

    /** Cheap AABB cull first; only then (cached) per-chunk work. */
    public boolean regionHasHighlights(MapTarget t, String dim, int rx, int rz) {
        if (!settings.active(t)) return false;
        DimView v = dims.get(dim);
        if (v == null || !aabbMayHit(v, rx, rz)) return false;
        synchronized (this) {
            return region(v, dim, rx, rz).any;
        }
    }

    /** Colours for Xaero ({@code [centre, N, E, S, W]} as ARGB) or null if the chunk is not highlit. */
    public int[] colors(MapTarget t, String dim, int cx, int cz) {
        if (!settings.active(t)) return null;
        DimView v = dims.get(dim);
        if (v == null) return null;
        int rx = Math.floorDiv(cx, REGION_CHUNKS), rz = Math.floorDiv(cz, REGION_CHUNKS);
        if (!aabbMayHit(v, rx, rz)) return null;
        int li = (Math.floorMod(cz, REGION_CHUNKS) << 5) | Math.floorMod(cx, REGION_CHUNKS);
        synchronized (this) {
            Region r = region(v, dim, rx, rz);
            if (!r.any || r.tip[li] == null) return null;
            int[] out = new int[5];
            System.arraycopy(r.colors, li * 5, out, 0, 5);
            return out;
        }
    }

    /** Fills a 16x16 BGRA raster (Xaero format) for the chunk; false if the chunk is not highlit. */
    public boolean raster(MapTarget t, String dim, int cx, int cz, int[] out) {
        if (!settings.active(t)) return false;
        DimView v = dims.get(dim);
        if (v == null) return false;
        int rx = Math.floorDiv(cx, REGION_CHUNKS), rz = Math.floorDiv(cz, REGION_CHUNKS);
        if (!aabbMayHit(v, rx, rz)) return false;
        int li = (Math.floorMod(cz, REGION_CHUNKS) << 5) | Math.floorMod(cx, REGION_CHUNKS);
        int[] c = new int[5];
        int mask;
        synchronized (this) {
            Region r = region(v, dim, rx, rz);
            if (!r.any || r.tip[li] == null) return false;
            System.arraycopy(r.colors, li * 5, c, 0, 5);
            mask = r.sides[li];
        }
        return ChunkHighlightRaster.render(c, mask, cx, cz, settings, out);
    }

    public boolean chunkIsHighlit(MapTarget t, String dim, int cx, int cz) {
        return colors(t, dim, cx, cz) != null;
    }

    /** Zone whose colour is shown on this chunk (fill zone, else the strongest border zone), or null. */
    public MapZone zoneForTooltip(MapTarget t, String dim, int cx, int cz) {
        if (!settings.active(t)) return null;
        DimView v = dims.get(dim);
        if (v == null) return null;
        int rx = Math.floorDiv(cx, REGION_CHUNKS), rz = Math.floorDiv(cz, REGION_CHUNKS);
        if (!aabbMayHit(v, rx, rz)) return null;
        synchronized (this) {
            return region(v, dim, rx, rz).tip[(Math.floorMod(cz, REGION_CHUNKS) << 5) | Math.floorMod(cx, REGION_CHUNKS)];
        }
    }

    /** Tooltip text (label, else name) or null. */
    public String tooltip(MapTarget t, String dim, int cx, int cz) {
        MapZone z = zoneForTooltip(t, dim, cx, cz);
        return z == null ? null : z.tooltip();
    }

    /** Region (with one chunk of margin) intersects at least one visible zone's bounds, or a default zone fills. */
    private static boolean aabbMayHit(DimView v, int rx, int rz) {
        double minX = rx * REGION_BLOCKS - 16, maxX = rx * REGION_BLOCKS + REGION_BLOCKS + 16;
        double minZ = rz * REGION_BLOCKS - 16, maxZ = rz * REGION_BLOCKS + REGION_BLOCKS + 16;
        for (int i = 0; i < v.zones.length; i++) {
            double[] b = v.bounds[i];
            if (b == null) return true; // default zone with fill
            if (b[0] <= maxX && minX <= b[1] && b[2] <= maxZ && minZ <= b[3]) return true;
        }
        return false;
    }

    private Region region(DimView v, String dim, int rx, int rz) {
        RegionKey key = new RegionKey(dim, rx, rz);
        Region r = cache.get(key);
        if (r == null) {
            r = compute(v, rx, rz);
            cache.put(key, r);
        }
        return r;
    }

    private Region compute(DimView v, int rx, int rz) {
        computations++;
        double mult = settings.opacityMultiplier();
        int n = v.zones.length;
        int baseX = rx * REGION_CHUNKS - 1, baseZ = rz * REGION_CHUNKS - 1;
        double rMinX = rx * REGION_BLOCKS - 16, rMaxX = rx * REGION_BLOCKS + REGION_BLOCKS + 16;
        double rMinZ = rz * REGION_BLOCKS - 16, rMaxZ = rz * REGION_BLOCKS + REGION_BLOCKS + 16;

        // membership grids (34x34) only for zones that can touch this region
        boolean[][] in = new boolean[n][];
        int[] argb = new int[n];
        for (int i = 0; i < n; i++) {
            MapZone z = v.zones[i];
            argb[i] = z.argb(mult);
            double[] b = v.bounds[i];
            if (b != null && !(b[0] <= rMaxX && rMinX <= b[1] && b[2] <= rMaxZ && rMinZ <= b[3])) continue;
            boolean[] g = new boolean[GRID * GRID];
            for (int gz = 0; gz < GRID; gz++) {
                for (int gx = 0; gx < GRID; gx++) g[gz * GRID + gx] = z.containsChunk(baseX + gx, baseZ + gz);
            }
            in[i] = g;
        }

        Region out = new Region();
        for (int lz = 0; lz < REGION_CHUNKS; lz++) {
            for (int lx = 0; lx < REGION_CHUNKS; lx++) {
                int gi = (lz + 1) * GRID + (lx + 1);
                int li = lz * REGION_CHUNKS + lx;
                int center = 0;
                MapZone tip = null;
                for (int i = 0; i < n; i++) { // zone of the chunk: first containing, by priority
                    if (in[i] != null && in[i][gi]) {
                        if (!v.zones[i].shadowsFill()) continue; // map-only outline (edge) does not hide the fill below it
                        if (v.zones[i].fill() && argb[i] != 0) { center = argb[i]; tip = v.zones[i]; }
                        break;
                    }
                }
                int[] side = {center, center, center, center};
                // N (-Z), E (+X), S (+Z), W (-X)
                int[] nb = {gi - GRID, gi + 1, gi + GRID, gi - 1};
                boolean border = false;
                int mask = 0;
                for (int s = 0; s < 4; s++) {
                    for (int i = 0; i < n; i++) {
                        MapZone z = v.zones[i];
                        if (!z.outline() || argb[i] == 0 || in[i] == null) continue;
                        boolean me = in[i][gi], nei = in[i][nb[s]];
                        if (z.outer() ? (!me && nei) : (me && !nei)) {
                            side[s] = argb[i];
                            if (tip == null) tip = z;
                            border = true;
                            mask |= 1 << s;
                            break;
                        }
                    }
                }
                if (center == 0 && !border) continue;
                int o = li * 5;
                out.colors[o] = center;
                out.colors[o + 1] = side[0];
                out.colors[o + 2] = side[1];
                out.colors[o + 3] = side[2];
                out.colors[o + 4] = side[3];
                out.sides[li] = (byte) mask;
                out.tip[li] = tip;
                out.any = true;
            }
        }
        return out;
    }
}
