package com.poligorn.ikariaborders.map;

/**
 * 16x16 pixel raster of one map chunk in Xaero BGRA (see {@link XaeroColor}); pure JVM.
 * Pixel index {@code (z << 4) | x}, same as Xaero's {@code setResult}. Priority: outline &gt; hatch &gt; fill &gt; 0.
 * Hatch is a diagonal in world pixels, so it continues across chunk borders.
 */
public final class ChunkHighlightRaster {
    public static final int SIZE = 16;
    public static final int N = 1, E = 2, S = 4, W = 8; // side mask bits (N = -Z, E = +X, S = +Z, W = -X)

    private ChunkHighlightRaster() {}

    private static final int MAX_PATTERNS = 512;
    private static final java.util.concurrent.ConcurrentHashMap<PatternKey, int[]> PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();

    private record PatternKey(int fill, int hatch, int step, int hw, int phase) {}

    private static int[] pattern(PatternKey k) {
        int[] p = new int[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int d = Math.floorMod(k.phase + x + z, k.step);
                int c = d < k.hw ? k.hatch : k.fill;
                if (c == 0) c = k.fill;
                p[(z << 4) | x] = c;
            }
        }
        return p;
    }

    /**
     * @param colors logical [centre, N, E, S, W] ARGB from {@link ZoneMapModel#colors}
     * @param sides  mask of sides that carry a border (a border colour may equal the fill colour)
     * @param out    256 ints, fully overwritten
     * @return false (and out zeroed) if nothing is drawn
     */
    public static boolean render(int[] colors, int sides, int cx, int cz, MapSettings s, int[] out) {
        int center = colors[0];
        if (center == 0 && sides == 0) {
            java.util.Arrays.fill(out, 0);
            return false;
        }
        double mult = s.opacityMultiplier();
        int w = s.outlineWidth();
        int step = s.hatchStep(), hw = Math.min(s.hatchWidth(), step);
        int fill = center == 0 ? 0 : XaeroColor.pack(center & 0xFFFFFF, (center >>> 24));
        int hatch = center == 0 ? 0 : XaeroColor.pack(center & 0xFFFFFF, s.hatchOpacity() * mult);
        if (sides == 0) { // uniform fill+hatch: only `step` distinct patterns exist
            int phase = Math.floorMod((cx + cz) * SIZE, step);
            int[] p = PATTERNS.computeIfAbsent(new PatternKey(fill, hatch, step, hw, phase), k -> pattern(k));
            if (PATTERNS.size() > MAX_PATTERNS) PATTERNS.clear();
            System.arraycopy(p, 0, out, 0, SIZE * SIZE);
            return true;
        }
        java.util.Arrays.fill(out, 0);
        int lineAlpha = (int) Math.round(255 * Math.min(1, mult));
        int[] line = new int[4];
        for (int i = 0; i < 4; i++) line[i] = (sides & (1 << i)) != 0 ? XaeroColor.pack(colors[i + 1] & 0xFFFFFF, lineAlpha) : 0;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int c = 0;
                if (z < w) c = line[0];
                if (c == 0 && x >= SIZE - w) c = line[1];
                if (c == 0 && z >= SIZE - w) c = line[2];
                if (c == 0 && x < w) c = line[3];
                if (c == 0 && center != 0) {
                    int d = Math.floorMod(cx * SIZE + x + cz * SIZE + z, step);
                    c = d < hw ? hatch : fill;
                    if (c == 0) c = fill;
                }
                out[(z << 4) | x] = c;
            }
        }
        return true;
    }
}
