package com.poligorn.ikariaborders.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/** 0.5a: cheap existence checks, raster pattern cache, no-op updates, thread safety, micro-benchmark (log only). */
class MapPerfTest {
    static final String OW = ZoneMapModelTest.OW;
    static final MapTarget WM = MapTarget.WORLD_MAP;

    /** Copy of the pre-0.5a formula of ChunkHighlightRaster.render. */
    static boolean reference(int[] colors, int sides, int cx, int cz, MapSettings s, int[] out) {
        java.util.Arrays.fill(out, 0);
        int center = colors[0];
        if (center == 0 && sides == 0) return false;
        double mult = s.opacityMultiplier();
        int w = s.outlineWidth();
        int step = s.hatchStep(), hw = Math.min(s.hatchWidth(), step);
        int fill = center == 0 ? 0 : XaeroColor.pack(center & 0xFFFFFF, (center >>> 24));
        int hatch = center == 0 ? 0 : XaeroColor.pack(center & 0xFFFFFF, s.hatchOpacity() * mult);
        int lineAlpha = (int) Math.round(255 * Math.min(1, mult));
        int[] line = new int[4];
        for (int i = 0; i < 4; i++) line[i] = (sides & (1 << i)) != 0 ? XaeroColor.pack(colors[i + 1] & 0xFFFFFF, lineAlpha) : 0;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int c = 0;
            if (z < w) c = line[0];
            if (c == 0 && x >= 16 - w) c = line[1];
            if (c == 0 && z >= 16 - w) c = line[2];
            if (c == 0 && x < w) c = line[3];
            if (c == 0 && center != 0) {
                int d = Math.floorMod(cx * 16 + x + cz * 16 + z, step);
                c = d < hw ? hatch : fill;
                if (c == 0) c = fill;
            }
            out[(z << 4) | x] = c;
        }
        return true;
    }

    @Test
    void cachedRasterMatchesReferenceOnAllPhases() {
        int[] cs = {-100, -17, -4, -3, -2, -1, 0, 1, 2, 3, 5, 50, 1000};
        int[] a = new int[256], b = new int[256];
        int[][] colorSets = {{0xFFFF3030 & 0x4DFFFFFF | 0x4D000000, 0, 0, 0, 0}, {0x80FF3030, 0x80FF3030, 0, 0x80FF3030, 0}};
        for (int step : new int[]{2, 3, 4, 7, 16}) for (int hw : new int[]{1, 2, 5, 16}) for (double ho : new double[]{0.7, 0}) {
            MapSettings s = new MapSettings(true, true, true, 0.8, Set.of(), 2, step, hw, ho);
            for (int[] c : colorSets) for (int mask : new int[]{0, 1, 2, 5, 15}) {
                for (int cx : cs) for (int cz : cs) {
                    boolean r1 = ChunkHighlightRaster.render(c, mask, cx, cz, s, a);
                    boolean r2 = reference(c, mask, cx, cz, s, b);
                    assertEquals(r2, r1);
                    assertArrayEquals(b, a, "step=" + step + " hw=" + hw + " mask=" + mask + " cx=" + cx + " cz=" + cz);
                }
            }
        }
    }

    @Test
    void existenceChecksWithWildNeverCompute() throws Exception {
        ZoneMapModel m = ZoneMapModelTest.exampleModel();
        for (int rx = -20; rx <= 20; rx += 3) for (int rz = -20; rz <= 20; rz += 7) assertTrue(m.regionHasHighlights(WM, OW, rx, rz));
        assertTrue(m.regionHasHighlights(WM, OW, 20, 20));
        assertEquals(0, m.computations());
        assertEquals(0, m.cachedRegions());
        // the compute happens on the actual paint/tooltip path
        int cx = 20 * 32 + 5, cz = 20 * 32 + 5;
        int[] out = new int[256];
        assertTrue(m.raster(WM, OW, cx, cz, out));
        assertNotNull(m.colors(WM, OW, cx, cz));
        assertNotNull(m.zoneForTooltip(WM, OW, cx, cz));
        assertEquals(1, m.computations());
    }

    @Test
    void existenceChecksWithoutDefaultUseAabbOnly() {
        ZoneMapModel m = ZoneMapModelTest.model(ZoneMapModelTest.sq("build", 100, 20,
                "{\"show\":true,\"style\":\"outline\",\"color\":\"#3399FF\"}", false));
        assertFalse(m.regionHasHighlights(WM, OW, 20, 20));
        assertFalse(m.regionHasHighlights(WM, OW, -5, 0));
        assertFalse(m.regionHasHighlights(WM, "minecraft:the_end", 0, 0));
        assertEquals(0, m.computations());
        assertTrue(m.regionHasHighlights(WM, OW, 0, 0));
        assertTrue(m.regionHasHighlights(WM, OW, -1, -1)); // zone edge reaches into the margin
        assertEquals(0, m.computations());
        assertEquals(0, m.cachedRegions());
        // 0.4f: interior chunk is highlit for the tooltip but draws nothing
        int[] out = new int[256];
        assertNotNull(m.zoneForTooltip(WM, OW, 0, 0));
        assertFalse(m.raster(WM, OW, 0, 0, out));
        assertTrue(m.raster(WM, OW, 5, 0, out)); // east border chunk (centre 88 < 100, neighbour 104 outside)
    }

    @Test
    void repeatedUpdatesAreNoOps() throws Exception {
        ZoneMapModel m = ZoneMapModelTest.exampleModel();
        ZoneMapData d = m.data();
        m.colors(WM, OW, 0, 0);
        m.colors(WM, OW, 100, 100);
        int cached = m.cachedRegions(), comps = m.computations();
        assertTrue(cached >= 2);
        assertFalse(m.setData(d));
        assertFalse(m.setSettings(MapSettings.DEFAULT));
        assertFalse(m.setSettings(new MapSettings(true, true, true, 1.0, Set.of())));
        m.colors(WM, OW, 0, 0);
        assertEquals(cached, m.cachedRegions());
        assertEquals(comps, m.computations());

        assertTrue(m.setSettings(new MapSettings(true, true, true, 0.5, Set.of())));
        assertEquals(0, m.cachedRegions());
        assertFalse(m.setSettings(new MapSettings(true, true, true, 0.5, Set.of())));
        assertTrue(m.setSettings(new MapSettings(true, true, true, 0.5, Set.of("spawn"))));
        m.colors(WM, OW, 0, 0);
        assertTrue(m.cachedRegions() > 0);
        ZoneMapData other = ZoneMapModelTest.model(ZoneMapModelTest.sq("z", 50, 1, "{\"show\":true}", false)).data();
        assertTrue(m.setData(other));
        assertEquals(0, m.cachedRegions());
        assertFalse(m.setData(other));
    }

    @Test
    void concurrentAccessIsConsistent() throws Exception {
        ZoneMapModel m = ZoneMapModelTest.exampleModel();
        ZoneMapModel ref = ZoneMapModelTest.exampleModel();
        int threads = 8, n = 3000;
        int[][] samples = new int[n][2];
        java.util.Random rnd = new java.util.Random(1);
        for (int[] p : samples) { p[0] = rnd.nextInt(2400) - 1200; p[1] = rnd.nextInt(2400) - 1200; }
        int[][] expColors = new int[n][];
        boolean[] expRaster = new boolean[n];
        int[][] expPix = new int[n][256];
        String[] expTip = new String[n];
        for (int i = 0; i < n; i++) {
            expColors[i] = ref.colors(WM, OW, samples[i][0], samples[i][1]);
            expRaster[i] = ref.raster(WM, OW, samples[i][0], samples[i][1], expPix[i]);
            expTip[i] = ref.tooltip(WM, OW, samples[i][0], samples[i][1]);
        }
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CyclicBarrier go = new CyclicBarrier(threads);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            fs.add(ex.submit(() -> {
                go.await();
                int[] out = new int[256];
                for (int k = 0; k < n; k++) {
                    int i = (k * 7 + seed * 131) % n;
                    int cx = samples[i][0], cz = samples[i][1];
                    m.regionHasHighlights(WM, OW, Math.floorDiv(cx, 32), Math.floorDiv(cz, 32));
                    assertArrayEquals(expColors[i], m.colors(WM, OW, cx, cz));
                    assertEquals(expRaster[i], m.raster(WM, OW, cx, cz, out));
                    if (expRaster[i]) assertArrayEquals(expPix[i], out);
                    assertEquals(expTip[i], m.tooltip(WM, OW, cx, cz));
                }
                return null;
            }));
        }
        for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        ex.shutdownNow();
        assertTrue(m.cachedRegions() <= ZoneMapModel.MAX_REGIONS);
    }

    @Test
    @Tag("perf")
    void microBenchmarkLogOnly() throws Exception {
        ZoneMapModel m = ZoneMapModelTest.exampleModel();
        for (int i = 0; i < 3; i++) scan(m);
        long t0 = System.nanoTime();
        int hits = scan(m);
        long scanNs = System.nanoTime() - t0;
        System.out.printf("[perf] regionHasHighlights 40x40 grid: %.3f ms, hits=%d, computations=%d%n",
                scanNs / 1e6, hits, m.computations());
        assertEquals(1600, hits);
        assertEquals(0, m.computations());

        int[] out = new int[256];
        for (int w = 0; w < 3; w++) paintRegion(new ZoneMapModel(), ZoneMapModelTest.exampleModel(), out);
        ZoneMapModel fresh = ZoneMapModelTest.exampleModel();
        t0 = System.nanoTime();
        int painted = paintRegion(null, fresh, out);
        long regNs = System.nanoTime() - t0;
        System.out.printf("[perf] compute+raster of one region (1024 chunks): %.3f ms, painted=%d, computations=%d%n",
                regNs / 1e6, painted, fresh.computations());
        assertEquals(1024, painted);
        assertEquals(1, fresh.computations());
    }

    private static int scan(ZoneMapModel m) {
        int hits = 0;
        for (int rx = -20; rx < 20; rx++) for (int rz = -20; rz < 20; rz++) if (m.regionHasHighlights(WM, OW, rx, rz)) hits++;
        return hits;
    }

    private static int paintRegion(ZoneMapModel unused, ZoneMapModel m, int[] out) {
        int painted = 0;
        for (int cx = 640; cx < 672; cx++) for (int cz = 640; cz < 672; cz++) if (m.raster(WM, OW, cx, cz, out)) painted++;
        return painted;
    }
}
