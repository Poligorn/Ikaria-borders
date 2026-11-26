package com.poligorn.ikariaborders.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side chunk painter (TZ §11). Pure JVM; the Xaero adapters only translate calls.
 *
 * <p>Semantics (per dimension, among visible zones = {@code map.show} and not in {@code hiddenZones}):
 * <ul>
 *   <li><b>Zone of a chunk</b> = highest priority zone containing the chunk centre (cx*16+8, cz*16+8); ties: declared first.
 *       Fill colour comes from that zone if its style has {@code fill}. The same zone supplies the tooltip over its whole
 *       area (outline-only zones too: interior chunks are "highlit" transparently, nothing is drawn); map-only zones
 *       do not label their interior, only their own border chunks.</li>
 *   <li><b>Side colours</b>: for each of N/E/S/W the highest-priority zone with {@code outline} for which this side is
 *       a border of <i>that zone's own shape</i> (side {@code inner}: chunk inside, neighbour outside;
 *       {@code outer}: chunk outside, neighbour inside). So a map-only zone nested under others only outlines its own edge.</li>
 *   <li>Colour layout of {@code getColors}: [0]=centre, [1]=north (-Z), [2]=east (+X), [3]=south (+Z), [4]=west (-X),
 *       derived from the bytecode of Xaero's {@code ChunkHighlighter.getChunkHighlitColor}; a side equal to the centre means "no border".</li>
 * </ul>
 * Regions of 32x32 chunks are computed once and kept in an LRU (256). Thread-safe: regions are computed outside locks, only the LRU get/put is locked; existence checks never compute.
 */
public final class ZoneMapModel {
    public static final int REGION_CHUNKS = 32;
    public static final int REGION_BLOCKS = REGION_CHUNKS * 16;
    public static final int MAX_REGIONS = 256;
    private static final int GRID = REGION_CHUNKS + 2; // one chunk margin for neighbour checks

    /** Immutable snapshot of data+settings+views with its own region cache; replaced atomically (stale puts hit the old cache). */
    private static final class State {
        final ZoneMapData data;
        final MapSettings settings;
        final Map<String, DimView> dims;
        final Map<RegionKey, Region> cache = new LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<RegionKey, Region> e) {
                return size() > MAX_REGIONS;
            }
        };
        State(ZoneMapData data, MapSettings settings, Map<String, DimView> dims) {
            this.data = data; this.settings = settings; this.dims = dims;
        }
        Region get(RegionKey k) { synchronized (cache) { return cache.get(k); } }
        Region putIfAbsent(RegionKey k, Region r) {
            synchronized (cache) {
                Region old = cache.get(k);
                if (old != null) return old;
                cache.put(k, r);
                return r;
            }
        }
        int size() { synchronized (cache) { return cache.size(); } }
    }

    private volatile State state = new State(ZoneMapData.EMPTY, MapSettings.DEFAULT, Map.of());
    private final AtomicInteger computations = new AtomicInteger(); // test hook

    private record RegionKey(String dim, int rx, int rz) {}

    private static final class DimView {
        final MapZone[] zones;     // visible, priority desc then declaration order
        final double[][] bounds;   // per zone [minX,maxX,minZ,maxZ]; null for default zones
        final boolean defaultFill; // a visible default zone with fill covers the whole dimension
        DimView(MapZone[] zones) {
            this.zones = zones;
            boolean df = false;
            for (MapZone z : zones) if (z.shape() == null) df = true;
            this.defaultFill = df;
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

    /** @return true if the model changed (version is a content hash, so equal version = same map data). */
    public synchronized boolean setData(ZoneMapData d) {
        Objects.requireNonNull(d);
        State cur = state;
        if (d.version() == cur.data.version()) return false;
        state = rebuild(d, cur.settings);
        return true;
    }

    /** @return true if the model changed. */
    public synchronized boolean setSettings(MapSettings s) {
        Objects.requireNonNull(s);
        State cur = state;
        if (s.equals(cur.settings)) return false;
        state = rebuild(cur.data, s);
        return true;
    }

    public MapSettings settings() { return state.settings; }
    public ZoneMapData data() { return state.data; }
    public long dataVersion() { return state.data.version(); }
    public int computations() { return computations.get(); }
    public int cachedRegions() { return state.size(); }

    private static State rebuild(ZoneMapData data, MapSettings settings) {
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
        return new State(data, settings, out);
    }

    public boolean active(MapTarget t) {
        return state.settings.active(t);
    }

    /** Value for Xaero's {@code calculateRegionHash}: changes with the config version and client settings. */
    public int regionHash(MapTarget t) {
        State s = state;
        return Objects.hash(s.data.version(), s.settings.hash(t));
    }

    /**
     * Existence check for Xaero (called for every visible leaf): never computes and never touches the LRU.
     * Default fill zone: always true. Otherwise AABB only (may be a false positive on the 1-chunk margin;
     * Xaero then asks {@code chunkIsHighlit} per tile).
     */
    public boolean regionHasHighlights(MapTarget t, String dim, int rx, int rz) {
        State s = state;
        if (!s.settings.active(t)) return false;
        DimView v = s.dims.get(dim);
        if (v == null) return false;
        return v.defaultFill || aabbMayHit(v, rx, rz);
    }

    /** Colours for Xaero ({@code [centre, N, E, S, W]} as ARGB) or null if the chunk is not highlit. */
    public int[] colors(MapTarget t, String dim, int cx, int cz) {
        State s = state;
        if (!s.settings.active(t)) return null;
        DimView v = s.dims.get(dim);
        if (v == null) return null;
        int rx = Math.floorDiv(cx, REGION_CHUNKS), rz = Math.floorDiv(cz, REGION_CHUNKS);
        if (!aabbMayHit(v, rx, rz)) return null;
        int li = (Math.floorMod(cz, REGION_CHUNKS) << 5) | Math.floorMod(cx, REGION_CHUNKS);
        Region r = region(s, v, dim, rx, rz);
        if (!r.any || r.tip[li] == null) return null;
        int[] out = new int[5];
        System.arraycopy(r.colors, li * 5, out, 0, 5);
        return out;
    }

    /** Fills a 16x16 BGRA raster (Xaero format) for the chunk; false if the chunk is not highlit. */
    public boolean raster(MapTarget t, String dim, int cx, int cz, int[] out) {
        State s = state;
        if (!s.settings.active(t)) return false;
        DimView v = s.dims.get(dim);
        if (v == null) return false;
        int rx = Math.floorDiv(cx, REGION_CHUNKS), rz = Math.floorDiv(cz, REGION_CHUNKS);
        if (!aabbMayHit(v, rx, rz)) return false;
        int li = (Math.floorMod(cz, REGION_CHUNKS) << 5) | Math.floorMod(cx, REGION_CHUNKS);
        int[] c = new int[5];
        int mask;
        Region r = region(s, v, dim, rx, rz);
        if (!r.any || r.tip[li] == null) return false;
        System.arraycopy(r.colors, li * 5, c, 0, 5);
        mask = r.sides[li];
        return ChunkHighlightRaster.render(c, mask, cx, cz, s.settings, out);
    }

    public boolean chunkIsHighlit(MapTarget t, String dim, int cx, int cz) {
        return colors(t, dim, cx, cz) != null;
    }

    /** Tooltip zone: first (by priority) non-map-only zone containing the chunk, even without fill; else the border zone drawn on it; or null. */
    public MapZone zoneForTooltip(MapTarget t, String dim, int cx, int cz) {
        State s = state;
        if (!s.settings.active(t)) return null;
        DimView v = s.dims.get(dim);
        if (v == null) return null;
        int rx = Math.floorDiv(cx, REGION_CHUNKS), rz = Math.floorDiv(cz, REGION_CHUNKS);
        if (!aabbMayHit(v, rx, rz)) return null;
        return region(s, v, dim, rx, rz).tip[(Math.floorMod(cz, REGION_CHUNKS) << 5) | Math.floorMod(cx, REGION_CHUNKS)];
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

    /** Compute outside any lock; if two threads race on one key, the first stored region wins. */
    private Region region(State s, DimView v, String dim, int rx, int rz) {
        RegionKey key = new RegionKey(dim, rx, rz);
        Region r = s.get(key);
        if (r != null) return r;
        return s.putIfAbsent(key, compute(s, v, rx, rz));
    }

    private Region compute(State st, DimView v, int rx, int rz) {
        computations.incrementAndGet();
        double mult = st.settings.opacityMultiplier();
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
                        tip = v.zones[i]; // tooltip covers the whole zone, even outline-only
                        if (v.zones[i].fill() && argb[i] != 0) center = argb[i];
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
                if (center == 0 && !border && tip == null) continue; // tip-only chunk: highlit transparently, nothing drawn
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
