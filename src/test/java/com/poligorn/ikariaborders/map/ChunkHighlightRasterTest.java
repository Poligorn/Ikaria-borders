package com.poligorn.ikariaborders.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChunkHighlightRasterTest {
    static final int RGB = 0x55FF55;
    static final int FILL = (0x40 << 24) | RGB; // logical ARGB @0.25
    static final MapSettings S = MapSettings.DEFAULT; // outline 2, step 4, width 1, hatch 0.7
    static final int FILL_PX = 0x55FF5540, LINE_PX = 0x55FF55FF, HATCH_PX = XaeroColor.pack(RGB, 0.7);

    static int[] render(int[] colors, int sides, int cx, int cz) {
        int[] out = new int[256];
        assertTrue(ChunkHighlightRaster.render(colors, sides, cx, cz, S, out));
        return out;
    }

    static int px(int[] o, int x, int z) { return o[(z << 4) | x]; }

    @Test
    void fillAndOutlineEastSide() {
        int[] o = render(new int[]{FILL, FILL, FILL, FILL, FILL}, ChunkHighlightRaster.E, 0, 0);
        for (int z = 0; z < 16; z++) {
            assertEquals(LINE_PX, px(o, 15, z));
            assertEquals(LINE_PX, px(o, 14, z));
            assertNotEquals(LINE_PX, px(o, 13, z));
        }
        boolean fill = false, hatch = false;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 14; x++) {
            int v = px(o, x, z);
            assertTrue(v == FILL_PX || v == HATCH_PX, "pixel " + x + "," + z);
            fill |= v == FILL_PX;
            hatch |= v == HATCH_PX;
        }
        assertTrue(fill && hatch);
        assertEquals(0x40, FILL_PX & 0xFF);
        assertTrue((HATCH_PX & 0xFF) > 0x40);
    }

    @Test
    void hatchContinuesAcrossChunkBorder() {
        int[] a = render(new int[]{FILL, FILL, FILL, FILL, FILL}, 0, 0, 0);
        int[] b = render(new int[]{FILL, FILL, FILL, FILL, FILL}, 0, 1, 0);
        // world px (15,z) of chunk 0 and (16,z) of chunk 1 differ by 1 along x: diagonal (x+z) % 4 shifts by one step
        for (int z = 0; z < 16; z++) {
            boolean last = px(a, 15, z) == HATCH_PX, first = px(b, 0, z) == HATCH_PX;
            boolean sameLine = (15 + z) % 4 < 1;
            assertEquals(sameLine, last);
            assertEquals((16 + z) % 4 < 1, first);
        }
        // the hatch line through (15,z) continues at (16, z-1)
        for (int z = 1; z < 16; z++) assertEquals(px(a, 15, z) == HATCH_PX, px(b, 0, z - 1) == HATCH_PX);
    }

    @Test
    void hatchNegativeChunks() {
        int[] o = render(new int[]{FILL, FILL, FILL, FILL, FILL}, 0, -1, -1);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            assertEquals(Math.floorMod(-16 + x - 16 + z, 4) == 0, px(o, x, z) == HATCH_PX, x + "," + z);
    }

    @Test
    void outlineOnlyHasNoFill() {
        int[] o = render(new int[]{0, LINE_ARGB(), 0, 0, 0}, ChunkHighlightRaster.N, 3, 3);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            assertEquals(z < 2 ? LINE_PX : 0, px(o, x, z), x + "," + z);
    }

    static int LINE_ARGB() { return FILL; }

    @Test
    void fillOnlyHasHatchButNoOpaqueLine() {
        int[] o = render(new int[]{FILL, FILL, FILL, FILL, FILL}, 0, 0, 0);
        boolean hatch = false;
        for (int v : o) {
            assertNotEquals(LINE_PX, v);
            hatch |= v == HATCH_PX;
        }
        assertTrue(hatch);
    }

    @Test
    void emptyIsNotDrawn() {
        int[] out = new int[256];
        java.util.Arrays.fill(out, 7);
        assertFalse(ChunkHighlightRaster.render(new int[5], 0, 0, 0, S, out));
        for (int v : out) assertEquals(0, v);
    }

    @Test
    void multiplierScalesEverything() {
        MapSettings half = new MapSettings(true, true, true, 0.5, java.util.Set.of());
        int[] out = new int[256];
        ChunkHighlightRaster.render(new int[]{0, FILL, 0, 0, 0}, ChunkHighlightRaster.N, 0, 0, half, out);
        assertEquals(XaeroColor.pack(RGB, (int) Math.round(255 * 0.5)), out[0]);
    }

    @Test
    void modelRasterForFillOutlineSpawn() {
        ZoneMapModel m = ZoneMapModelTest.model(ZoneMapModelTest.sq("spawn", 100, 30,
                "{\"show\":true,\"style\":\"fill+outline\",\"color\":\"#55FF55\",\"opacity\":0.25}", false));
        int[] o = new int[256];
        assertTrue(m.raster(MapTarget.WORLD_MAP, ZoneMapModelTest.OW, 5, 0, o)); // east edge: chunk 6 is outside
        assertEquals(LINE_PX, px(o, 15, 8));
        assertTrue(m.raster(MapTarget.WORLD_MAP, ZoneMapModelTest.OW, 0, 0, o)); // interior: no line
        for (int v : o) assertNotEquals(LINE_PX, v);
        assertFalse(m.raster(MapTarget.WORLD_MAP, ZoneMapModelTest.OW, 50, 0, o));
    }
}
