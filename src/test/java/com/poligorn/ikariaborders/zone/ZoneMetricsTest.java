package com.poligorn.ikariaborders.zone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneMetricsTest {
    static final String OW = "minecraft:overworld";
    static final double EPS = 1e-9;

    private static ZoneConfig cfg(String zones) {
        ConfigLoader.Result r = ConfigLoader.parse("{\"version\":1,\"zones\":[" + zones + "]}", null);
        assertTrue(r.ok(), () -> r.errors().toString());
        return r.config();
    }

    private static final String WILD = "{\"id\":\"wild\",\"dimension\":\"minecraft:overworld\",\"default\":true,\"priority\":0}";

    private static String sq(String id, int half, int pri) {
        return "{\"id\":\"" + id + "\",\"dimension\":\"minecraft:overworld\",\"priority\":" + pri
                + ",\"shape\":{\"type\":\"square\",\"center\":[0,0],\"half\":" + half + "}}";
    }

    private static double d(ZoneConfig c, String id, double x, double z) {
        return ZoneMetrics.distanceToBorder(c, c.zone(id), x, z);
    }

    @Test void squareHalf100() {
        ZoneConfig c = cfg(WILD + "," + sq("spawn", 100, 30));
        assertEquals(100, d(c, "spawn", 0, 0), EPS);
        assertEquals(50, d(c, "spawn", 50, 10), EPS, "nearest edge is x");
        assertEquals(1, d(c, "spawn", -99, 0), EPS);
        assertEquals(0, d(c, "spawn", 100, 0), EPS, "on the border");
        assertEquals(-50, d(c, "spawn", 150, 0), EPS);
        assertEquals(-50, d(c, "spawn", 0, -150), EPS);
        assertEquals(-Math.hypot(30, 40), d(c, "spawn", 130, 140), EPS, "outside a corner: euclidean distance to the corner");
    }

    @Test void squareHalf1500() {
        ZoneConfig c = cfg(WILD + "," + sq("build", 1500, 20));
        assertEquals(1500, d(c, "build", 0, 0), EPS);
        assertEquals(100, d(c, "build", 1400, 0), EPS);
        assertEquals(100, d(c, "build", 1400, 1400), EPS, "inside corner: distance to the nearest side");
        assertEquals(-1, d(c, "build", 1501, 0), EPS);
        assertEquals(500, ZoneMetrics.depth(c, c.zone("build"), 1000, 0), EPS);
        assertEquals(0, ZoneMetrics.depth(c, c.zone("build"), 2000, 0), EPS, "depth is 0 outside");
    }

    @Test void depthTiersFromSpecExample() {
        ZoneConfig c = cfg(WILD + "," + sq("build", 1500, 20));
        Zone wild = c.zone("wild");
        // 2600 blocks east of the centre: 1100 blocks beyond the build border -> tier floor(1100 / 500) = 2
        double depth = ZoneMetrics.depth(c, wild, 2600, 0);
        assertEquals(1100, depth, EPS);
        assertEquals(2, (int) Math.floor(depth / 500));
    }

    @Test void circle() {
        ZoneConfig c = cfg(WILD + ",{\"id\":\"c\",\"dimension\":\"minecraft:overworld\",\"priority\":10,\"shape\":{\"type\":\"circle\",\"center\":[100,100],\"radius\":50}}");
        assertEquals(50, d(c, "c", 100, 100), EPS);
        assertEquals(20, d(c, "c", 130, 100), EPS);
        assertEquals(0, d(c, "c", 150, 100), EPS);
        assertEquals(-25, d(c, "c", 175, 100), EPS);
        assertEquals(-10, d(c, "c", 100 + 36, 100 + 48), 1e-9, "diagonal: hypot(36,48)=60, 10 outside");
    }

    @Test void rectIsWholeBlocksInclusive() {
        ZoneConfig c = cfg(WILD + ",{\"id\":\"r\",\"dimension\":\"minecraft:overworld\",\"priority\":10,\"shape\":{\"type\":\"rect\",\"from\":[0,0],\"to\":[9,19]}}");
        // covers x in [0,10), z in [0,20)
        assertEquals(5, d(c, "r", 5, 10), EPS);
        assertEquals(0.5, d(c, "r", 9.5, 10), EPS, "x=9.5 is inside block 9; the edge is at 10");
        assertEquals(0, d(c, "r", 10, 10), EPS);
        assertEquals(-3, d(c, "r", 13, 10), EPS);
        assertEquals(-5, d(c, "r", -5, 10), EPS);
        assertEquals(-Math.hypot(3, 4), d(c, "r", 13, 24), EPS);
    }

    @Test void polygonNearestEdgeAndSign() {
        // 100x100 square as polygon
        ZoneConfig c = cfg(WILD + ",{\"id\":\"p\",\"dimension\":\"minecraft:overworld\",\"priority\":10,\"shape\":{\"type\":\"polygon\",\"points\":[[0,0],[100,0],[100,100],[0,100]]}}");
        assertEquals(50, d(c, "p", 50, 50), EPS);
        assertEquals(10, d(c, "p", 90, 50), EPS);
        assertEquals(0, d(c, "p", 100, 50), EPS);
        assertEquals(-20, d(c, "p", 120, 50), EPS);
        assertEquals(-Math.hypot(10, 10), d(c, "p", 110, 110), EPS);
    }

    @Test void concavePolygonUsesNearestEdgeNotNearestVertexSide() {
        // L shape: the notch is x in (50,100), z in (50,100)
        ZoneConfig c = cfg(WILD + ",{\"id\":\"l\",\"dimension\":\"minecraft:overworld\",\"priority\":10,\"shape\":{\"type\":\"polygon\",\"points\":[[0,0],[100,0],[100,50],[50,50],[50,100],[0,100]]}}");
        assertTrue(d(c, "l", 60, 60) < 0, "point in the notch is outside");
        assertEquals(-10, d(c, "l", 60, 60), EPS, "10 from both notch edges");
        assertEquals(Math.hypot(10, 10), d(c, "l", 40, 40), EPS, "nearest boundary point is the reflex vertex (50,50)");
    }

    @Test void unionAndDifference() {
        ZoneConfig c = cfg(WILD + ",{\"id\":\"u\",\"dimension\":\"minecraft:overworld\",\"priority\":10,\"shape\":{\"type\":\"union\",\"shapes\":["
                + "{\"type\":\"square\",\"center\":[0,0],\"half\":100},{\"type\":\"square\",\"center\":[300,0],\"half\":100}]}},"
                + "{\"id\":\"ring\",\"dimension\":\"minecraft:overworld\",\"priority\":11,\"shape\":{\"type\":\"difference\","
                + "\"base\":{\"type\":\"square\",\"center\":[0,0],\"half\":100},\"minus\":[{\"type\":\"square\",\"center\":[0,0],\"half\":40}]}}");
        assertEquals(100, d(c, "u", 0, 0), EPS);
        assertEquals(100, d(c, "u", 300, 0), EPS);
        assertEquals(-50, d(c, "u", 150, 0), EPS, "between the squares: 50 from each, union = max(-50, -50)");
        // ring: 70 from the centre is 30 inside the base and 30 outside the hole
        assertEquals(30, d(c, "ring", 70, 0), EPS);
        assertEquals(-40, d(c, "ring", 0, 0), EPS, "centre of the hole: 40 to the hole's edge");
    }

    @Test void defaultZoneBorderIsTheHigherPriorityZones() {
        ZoneConfig c = cfg(WILD + "," + sq("build", 1500, 20) + "," + sq("spawn", 100, 30));
        Zone wild = c.zone("wild");
        assertEquals(500, ZoneMetrics.distanceToBorder(c, wild, 2000, 0), EPS);
        assertEquals(-1500, ZoneMetrics.distanceToBorder(c, wild, 0, 0), EPS, "inside build: negative");
        assertEquals(0, ZoneMetrics.depth(c, wild, 0, 0), EPS);
        assertEquals(0, ZoneMetrics.depth(c, wild, 1500, 0), EPS, "at the border");
        assertEquals(1, ZoneMetrics.depth(c, wild, 1501, 0), EPS);
    }

    @Test void defaultZoneIgnoresMapOnlyAndLowerPriorityZones() {
        ZoneConfig c = cfg(WILD + ",{\"id\":\"edge\",\"dimension\":\"minecraft:overworld\",\"priority\":5,\"mapOnly\":true,"
                + "\"shape\":{\"type\":\"square\",\"center\":[0,0],\"half\":10000}}");
        assertEquals(0, ZoneMetrics.depth(c, c.zone("wild"), 20000, 0), EPS, "map-only zone is not a border");
        assertTrue(Double.isInfinite(ZoneMetrics.distanceToBorder(c, c.zone("wild"), 0, 0)));
    }

    @Test void zonesAtListsByPriorityDefaultLast() {
        ZoneConfig c = cfg(WILD + "," + sq("build", 1500, 20) + "," + sq("spawn", 100, 30));
        assertEquals(List.of("spawn", "build", "wild"), c.zonesAt(OW, 10, 10).stream().map(Zone::id).toList());
        assertEquals(List.of("wild"), c.zonesAt(OW, 5000, 0).stream().map(Zone::id).toList());
    }
}
