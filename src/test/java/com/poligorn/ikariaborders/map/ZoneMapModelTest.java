package com.poligorn.ikariaborders.map;

import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.zone.ConfigLoader;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

class ZoneMapModelTest {
    static final String OW = "minecraft:overworld";
    static final MapTarget WM = MapTarget.WORLD_MAP, MM = MapTarget.MINIMAP;
    // colour layout of Xaero getColors: centre, N(-Z), E(+X), S(+Z), W(-X)
    static final int C = 0, N = 1, E = 2, S = 3, W = 4;

    static ZoneConfig parse(String zones) {
        ConfigLoader.Result r = ConfigLoader.parse("{\"version\":1,\"zones\":[" + zones + "]}", null);
        assertTrue(r.ok(), () -> "config must be valid: " + r.errors());
        return r.config();
    }

    static String sq(String id, int half, int prio, String map, boolean mapOnly) {
        return "{\"id\":\"" + id + "\",\"name\":\"N-" + id + "\",\"dimension\":\"" + OW + "\",\"priority\":" + prio
                + (mapOnly ? ",\"mapOnly\":true" : "") + ",\"shape\":{\"type\":\"square\",\"center\":[0,0],\"half\":" + half
                + "},\"map\":" + map + "}";
    }

    static ZoneMapModel model(String zones) {
        ZoneMapModel m = new ZoneMapModel();
        m.setData(ZoneMapData.of(parse(zones)));
        return m;
    }

    static String exampleJson() throws IOException {
        try (InputStream in = ZoneMapModelTest.class.getResourceAsStream("/ikaria_borders/zones.example.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static ZoneMapModel exampleModel() throws IOException {
        ConfigLoader.Result r = ConfigLoader.parse(exampleJson(), null);
        assertTrue(r.ok(), () -> r.errors().toString());
        ZoneMapModel m = new ZoneMapModel();
        m.setData(ZoneMapData.of(r.config()));
        return m;
    }

    // ---- chunk-centre membership at half=100 / half=1500 ----

    @Test
    void chunksInsideSquare100UseCentreFormula() {
        ZoneMapModel m = model(sq("spawn", 100, 30, "{\"show\":true,\"style\":\"fill\",\"color\":\"#55FF55\",\"opacity\":0.25}", false));
        // centre = cx*16+8 must be strictly inside (-100,100): cx in [-6, 5]
        for (int cx = -10; cx <= 10; cx++) {
            boolean expected = cx >= -6 && cx <= 5;
            assertEquals(expected, m.chunkIsHighlit(WM, OW, cx, 0), "cx=" + cx);
            assertEquals(expected, m.chunkIsHighlit(WM, OW, 0, cx), "cz=" + cx);
        }
        int count = 0;
        for (int cx = -20; cx <= 20; cx++) for (int cz = -20; cz <= 20; cz++) if (m.chunkIsHighlit(WM, OW, cx, cz)) count++;
        assertEquals(12 * 12, count);
    }

    @Test
    void chunksInsideSquare1500UseCentreFormula() {
        ZoneMapModel m = model(sq("build", 1500, 20, "{\"show\":true,\"style\":\"fill\",\"color\":\"#FF3030\"}", false));
        // 1500/16 = 93.75: centre 93*16+8 = 1496 < 1500 (inside), 94*16+8 = 1512 (outside); negative side: -94 -> -1496
        assertTrue(m.chunkIsHighlit(WM, OW, 93, 0));
        assertFalse(m.chunkIsHighlit(WM, OW, 94, 0));
        assertTrue(m.chunkIsHighlit(WM, OW, -94, 0));
        assertFalse(m.chunkIsHighlit(WM, OW, -95, 0));
        // corner (1400,1400) lies in chunk 87 -> inside (square, not circle)
        assertTrue(m.chunkIsHighlit(WM, OW, 87, 87));
    }

    // ---- outline sides ----

    @Test
    void outlineDrawsInnerSidesAtSquareBorder() {
        ZoneMapModel m = model(sq("spawn", 100, 30, "{\"show\":true,\"style\":\"outline\",\"color\":\"#55FF55\",\"opacity\":1}", false));
        int green = 0xFF55FF55;
        // interior chunk: nothing
        assertNull(m.colors(WM, OW, 0, 0));
        // east edge chunk (cx=5): only east side coloured, centre transparent
        int[] e = m.colors(WM, OW, 5, 0);
        assertArrayEquals(new int[]{0, 0, green, 0, 0}, e);
        // west edge (cx=-6)
        assertArrayEquals(new int[]{0, 0, 0, 0, green}, m.colors(WM, OW, -6, 0));
        // north edge is -Z (cz=-6), south is +Z (cz=5)
        assertArrayEquals(new int[]{0, green, 0, 0, 0}, m.colors(WM, OW, 0, -6));
        assertArrayEquals(new int[]{0, 0, 0, green, 0}, m.colors(WM, OW, 0, 5));
        // corner chunk: two sides (east + south)
        assertArrayEquals(new int[]{0, 0, green, green, 0}, m.colors(WM, OW, 5, 5));
        // outside chunk (inner side): nothing
        assertNull(m.colors(WM, OW, 6, 0));
    }

    @Test
    void outerSideColoursTheNeighbourOutside() {
        ZoneMapModel m = model(sq("z", 100, 30, "{\"show\":true,\"style\":\"outline\",\"side\":\"outer\",\"color\":\"#FF0000\",\"opacity\":1}", false));
        int red = 0xFFFF0000;
        assertNull(m.colors(WM, OW, 5, 0)); // inside chunk: not painted
        // first chunk outside to the east: its WEST side faces the zone
        assertArrayEquals(new int[]{0, 0, 0, 0, red}, m.colors(WM, OW, 6, 0));
        assertArrayEquals(new int[]{0, 0, 0, red, 0}, m.colors(WM, OW, 0, -7)); // north outside: its south side faces the zone
    }

    // ---- styles ----

    @Test
    void fillOutlineAndFillPlusOutline() {
        String z = "{\"show\":true,\"color\":\"#0000FF\",\"opacity\":0.5,\"style\":\"%s\"}";
        int a = 128; // round(0.5 * 255)
        int blue = (a << 24) | 0x0000FF;

        ZoneMapModel fill = model(sq("z", 100, 1, z.formatted("fill"), false));
        assertArrayEquals(new int[]{blue, blue, blue, blue, blue}, fill.colors(WM, OW, 0, 0));
        assertArrayEquals(new int[]{blue, blue, blue, blue, blue}, fill.colors(WM, OW, 5, 0)); // fill only: border looks the same

        ZoneMapModel outline = model(sq("z", 100, 1, z.formatted("outline"), false));
        assertNull(outline.colors(WM, OW, 0, 0));
        assertArrayEquals(new int[]{0, 0, blue, 0, 0}, outline.colors(WM, OW, 5, 0));

        ZoneMapModel both = model(sq("z", 100, 1, z.formatted("fill+outline"), false));
        assertArrayEquals(new int[]{blue, blue, blue, blue, blue}, both.colors(WM, OW, 0, 0));
        assertNotNull(both.colors(WM, OW, 5, 0));
        assertEquals(blue, both.colors(WM, OW, 5, 0)[C]);
    }

    @Test
    void fillPlusOutlineUsesDifferentSideColourFromFillOpacity() throws IOException {
        // example config: spawn is fill+outline, green, opacity 0.25 -> centre and the border sides share one colour here,
        // and the build outline (red 0.6) must NOT leak into spawn chunks
        ZoneMapModel m = exampleModel();
        int[] mid = m.colors(WM, OW, 0, 0);
        assertNotNull(mid);
        int green25 = ((int) Math.round(0.25 * 255) << 24) | 0x55FF55;
        assertArrayEquals(new int[]{green25, green25, green25, green25, green25}, mid);
    }

    // ---- mapOnly edge + default zones ----

    @Test
    void mapOnlyEdgeIsDrawnAndInnerOutlineStaysOnItsOwnBorder() throws IOException {
        ZoneMapModel m = exampleModel();
        int purple = ((int) Math.round(0.6 * 255) << 24) | 0xAA00FF;
        // 10000 / 16 = 625: centre 624*16+8 = 9992 < 10000 inside, 625 -> 10008 outside
        int[] e = m.colors(WM, OW, 624, 0);
        assertNotNull(e, "mapOnly edge must be drawn");
        assertArrayEquals(new int[]{0, 0, purple, 0, 0}, e);
        assertNull(m.colors(WM, OW, 625, 0));
        assertArrayEquals(new int[]{0, 0, 0, 0, purple}, m.colors(WM, OW, -625, 0));
        // between 1500 and 10000 nothing is painted (edge is outline-only, no purple line at the build border)
        assertNull(m.colors(WM, OW, 100, 0));
        assertNull(m.colors(WM, OW, 94, 0));
        // build border (red) at 1500 sits on the build chunks only
        int red = ((int) Math.round(0.6 * 255) << 24) | 0xFF3030;
        assertArrayEquals(new int[]{0, 0, red, 0, 0}, m.colors(WM, OW, 93, 0));
    }

    @Test
    void defaultZonesWithShowFalseNeverFillTheDimension() throws IOException {
        ZoneMapData d = ZoneMapData.of(ConfigLoader.parse(exampleJson(), null).config());
        assertEquals(Set.of("build", "spawn", "edge"), d.zones().stream().map(MapZone::id).collect(java.util.stream.Collectors.toSet()));
        ZoneMapModel m = new ZoneMapModel();
        m.setData(d);
        assertNull(m.colors(WM, OW, 5000, 5000));
        assertNull(m.colors(WM, "minecraft:the_nether", 0, 0));
        assertFalse(m.regionHasHighlights(WM, "minecraft:the_nether", 0, 0));
    }

    @Test
    void defaultZoneWithShowTrueFillsEverything() {
        ZoneMapModel m = model("{\"id\":\"d\",\"dimension\":\"" + OW + "\",\"default\":true,\"priority\":0,"
                + "\"map\":{\"show\":true,\"style\":\"fill\",\"color\":\"#FFFFFF\",\"opacity\":1}}");
        assertNotNull(m.colors(WM, OW, 12345, -777));
        assertTrue(m.regionHasHighlights(WM, OW, 40, 40));
    }

    @Test
    void higherPriorityZoneWinsFill() {
        ZoneMapModel m = model(
                sq("low", 1500, 20, "{\"show\":true,\"style\":\"fill\",\"color\":\"#FF0000\",\"opacity\":1}", false) + ","
                        + sq("high", 100, 30, "{\"show\":true,\"style\":\"fill\",\"color\":\"#00FF00\",\"opacity\":1}", false));
        assertEquals(0xFF00FF00, m.colors(WM, OW, 0, 0)[C]);
        assertEquals(0xFFFF0000, m.colors(WM, OW, 50, 0)[C]);
    }

    @Test
    void showFalseZonesAreNotSentOrDrawn() {
        ZoneMapData d = ZoneMapData.of(parse(sq("hidden", 100, 1, "{\"show\":false}", false)));
        assertTrue(d.zones().isEmpty());
    }

    // ---- client settings ----

    @Test
    void hiddenZonesAndDisabledFlagsSkipDrawing() {
        ZoneMapModel m = model(sq("spawn", 100, 30, "{\"show\":true,\"style\":\"fill\"}", false) + ","
                + sq("build", 1500, 20, "{\"show\":true,\"style\":\"fill\"}", false));
        assertNotNull(m.colors(WM, OW, 0, 0));

        m.setSettings(new MapSettings(true, true, true, 1.0, Set.of("spawn")));
        assertEquals(m.data().zones().get(1).argb(1.0), m.colors(WM, OW, 0, 0)[C], "spawn hidden -> build shows through");

        m.setSettings(new MapSettings(true, true, true, 1.0, Set.of("spawn", "build")));
        assertNull(m.colors(WM, OW, 0, 0));
        assertFalse(m.regionHasHighlights(WM, OW, 0, 0));

        m.setSettings(new MapSettings(false, true, true, 1.0, Set.of()));
        assertNull(m.colors(WM, OW, 0, 0));
        assertNull(m.colors(MM, OW, 0, 0));
        assertFalse(m.chunkIsHighlit(WM, OW, 0, 0));
        assertFalse(m.regionHasHighlights(WM, OW, 0, 0));

        m.setSettings(new MapSettings(true, false, true, 1.0, Set.of()));
        assertNull(m.colors(WM, OW, 0, 0));
        assertNotNull(m.colors(MM, OW, 0, 0));

        m.setSettings(new MapSettings(true, true, false, 1.0, Set.of()));
        assertNotNull(m.colors(WM, OW, 0, 0));
        assertNull(m.colors(MM, OW, 0, 0));
    }

    @Test
    void opacityMultiplierScalesAlphaAndZeroHidesEverything() {
        ZoneMapModel m = model(sq("z", 100, 1, "{\"show\":true,\"style\":\"fill\",\"color\":\"#FFFFFF\",\"opacity\":0.5}", false));
        m.setSettings(new MapSettings(true, true, true, 0.5, Set.of()));
        assertEquals((64 << 24) | 0xFFFFFF, m.colors(WM, OW, 0, 0)[C]); // round(0.25*255)=64
        m.setSettings(new MapSettings(true, true, true, 0.0, Set.of()));
        assertNull(m.colors(WM, OW, 0, 0));
    }

    @Test
    void regionHashChangesWithVersionAndSettings() {
        ZoneMapModel m = model(sq("z", 100, 1, "{\"show\":true}", false));
        int h = m.regionHash(WM);
        m.setSettings(new MapSettings(true, true, true, 0.5, Set.of()));
        assertNotEquals(h, m.regionHash(WM));
        m.setSettings(MapSettings.DEFAULT);
        assertEquals(h, m.regionHash(WM));
        m.setData(ZoneMapData.of(parse(sq("z", 200, 1, "{\"show\":true}", false))));
        assertNotEquals(h, m.regionHash(WM));
        // toggling the minimap does not repaint the world map
        int wm = m.regionHash(WM);
        m.setSettings(new MapSettings(true, true, false, 1.0, Set.of()));
        assertEquals(wm, m.regionHash(WM));
    }

    // ---- region AABB cull + cache ----

    @Test
    void regionAabbCullSkipsFarRegionsWithoutComputing() {
        ZoneMapModel m = model(sq("spawn", 100, 30, "{\"show\":true,\"style\":\"fill+outline\"}", false));
        assertTrue(m.regionHasHighlights(WM, OW, 0, 0));
        int before = m.computations();
        assertEquals(1, before);
        // regions far away (and in other dimensions): false, nothing computed
        assertFalse(m.regionHasHighlights(WM, OW, 5, 5));
        assertFalse(m.regionHasHighlights(WM, OW, -3, 0));
        assertFalse(m.regionHasHighlights(WM, "minecraft:the_nether", 0, 0));
        assertNull(m.colors(WM, OW, 1000, 1000));
        assertEquals(before, m.computations());
        // cached: asking again does not recompute
        assertTrue(m.regionHasHighlights(WM, OW, 0, 0));
        assertNotNull(m.colors(WM, OW, 1, 1));
        assertEquals(before, m.computations());
    }

    @Test
    void regionWithZoneInsideButNoOutlineOrFillHasNoHighlights() {
        // build outline-only: region (0,0) is deep inside (no border chunk) -> false after the (cached) check
        ZoneMapModel m = model(sq("build", 1500, 20, "{\"show\":true,\"style\":\"outline\"}", false));
        assertFalse(m.regionHasHighlights(WM, OW, 0, 0));
        assertTrue(m.regionHasHighlights(WM, OW, 2, 0));   // x 1024..1535 contains the 1500 border
        assertTrue(m.regionHasHighlights(WM, OW, -3, 0));  // x -1536..-1025
    }

    @Test
    void regionCacheIsLru256() {
        ZoneMapModel m = model(sq("big", 1000000, 1, "{\"show\":true,\"style\":\"fill\"}", false));
        for (int i = 0; i < 400; i++) assertTrue(m.regionHasHighlights(WM, OW, i, 0));
        assertEquals(ZoneMapModel.MAX_REGIONS, m.cachedRegions());
    }

    @Test
    void bordersAreCorrectAcrossRegionBoundaries() {
        // border chunk at cx=93 (x<1500) lies in region 2 (chunks 64..95); neighbour cx=94 is in the same region,
        // but a zone with half=1024+... would put the border exactly at a region seam: half=512 -> chunks -32..31
        ZoneMapModel m = model(sq("z", 512, 1, "{\"show\":true,\"style\":\"outline\",\"opacity\":1,\"color\":\"#00FF00\"}", false));
        // centre of chunk 31 = 504 < 512 inside; chunk 32 = 520 outside. Chunk 31 is the last of region 0, chunk 32 first of region 1.
        int[] last = m.colors(WM, OW, 31, 0);
        assertNotNull(last);
        assertEquals(0xFF00FF00, last[E]);
        assertNull(m.colors(WM, OW, 32, 0));
        int[] first = m.colors(WM, OW, -32, 0);
        assertEquals(0xFF00FF00, first[W]);
    }

    // ---- tooltip ----

    @Test
    void tooltipUsesLabelThenName() {
        ZoneMapModel m = model(sq("a", 100, 30, "{\"show\":true,\"style\":\"fill\",\"label\":\"Моя подпись\"}", false) + ","
                + sq("b", 1500, 20, "{\"show\":true,\"style\":\"fill\"}", false));
        assertEquals("Моя подпись", m.tooltip(WM, OW, 0, 0));
        assertEquals("N-b", m.tooltip(WM, OW, 50, 0)); // no label -> name
        assertNull(m.tooltip(WM, OW, 500, 0));
        m.setSettings(new MapSettings(false, true, true, 1.0, Set.of()));
        assertNull(m.tooltip(WM, OW, 0, 0));
    }

    @Test
    void tooltipFallbackIsOnMapZoneItself() {
        MapZone z = new MapZone("id", "Имя", OW, null, 0, 0, 0xFFFFFF, true, false, 1f, false, null);
        assertEquals("Имя", z.tooltip());
        assertEquals("Имя", new MapZone("id", "Имя", OW, null, 0, 0, 0, true, false, 1f, false, "  ").tooltip());
        assertEquals("L", new MapZone("id", "Имя", OW, null, 0, 0, 0, true, false, 1f, false, "L").tooltip());
    }

    @Test
    void borderChunkTooltipComesFromTheBorderZone() throws IOException {
        ZoneMapModel m = exampleModel();
        assertEquals("Граница диких земель", m.tooltip(WM, OW, 93, 0)); // build label, border chunk
        assertEquals("Спавн", m.tooltip(WM, OW, 0, 0));
        assertEquals("Край мира", m.tooltip(WM, OW, 624, 0));
    }
}
