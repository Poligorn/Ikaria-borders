package com.poligorn.ikariaborders.zone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigValidationTest {
    private static ConfigLoader.Result parse(String zones) {
        return ConfigLoader.parse("{\"version\":1,\"zones\":[" + zones + "]}", null);
    }

    private static void assertErrorAt(ConfigLoader.Result r, String path) {
        assertFalse(r.ok(), "must be rejected");
        assertTrue(r.errors().stream().anyMatch(e -> e.path().equals(path)),
                () -> "expected error at " + path + " but got " + r.errors());
    }

    private static final String D = "\"dimension\":\"minecraft:overworld\"";

    @Test void invalidJson() {
        ConfigLoader.Result r = ConfigLoader.parse("{ not json", null);
        assertFalse(r.ok());
        assertEquals("$", r.errors().get(0).path());
        assertFalse(ConfigLoader.parse("[]", null).ok());
    }

    @Test void missingVersionAndZones() {
        ConfigLoader.Result r = ConfigLoader.parse("{}", null);
        assertErrorAt(r, "version");
        assertErrorAt(r, "zones");
        assertErrorAt(ConfigLoader.parse("{\"version\":2,\"zones\":[]}", null), "version");
    }

    @Test void badShapeFieldPathIsReported() {
        // zones[2].shape.half — the example path from TZ §8.3
        ConfigLoader.Result r = parse("""
            {"id":"a",%1$s,"default":true},
            {"id":"b",%1$s,"shape":{"type":"square","center":[0,0],"half":10}},
            {"id":"c",%1$s,"shape":{"type":"square","center":[0,0],"half":-5}}""".formatted(D));
        assertErrorAt(r, "zones[2].shape.half");
        assertTrue(r.errors().stream().anyMatch(e -> e.toString().startsWith("zones[2].shape.half: ")));
    }

    @Test void wrongTypes() {
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"priority\":\"high\"}"), "zones[0].priority");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"priority\":1.5}"), "zones[0].priority");
        assertErrorAt(parse("{\"id\":5," + D + ",\"default\":true}"), "zones[0].id");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":\"yes\"}"), "zones[0].default");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"rules\":{\"pvp\":\"yes\"}}"), "zones[0].rules.pvp");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"rules\":{\"claims\":{\"allow\":1}}}"), "zones[0].rules.claims.allow");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"map\":{\"color\":\"red\"}}"), "zones[0].map.color");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"map\":{\"opacity\":2}}"), "zones[0].map.opacity");
    }

    @Test void nestedShapePaths() {
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"union\",\"shapes\":[{\"type\":\"circle\",\"center\":[0,0],\"radius\":0}]}}"),
                "zones[0].shape.shapes[0].radius");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"difference\",\"base\":{\"type\":\"square\",\"center\":[0,\"x\"],\"half\":5},\"minus\":[]}}"),
                "zones[0].shape.base.center[1]");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"difference\",\"base\":{\"type\":\"square\",\"center\":[0,0],\"half\":5},\"minus\":[]}}"),
                "zones[0].shape.minus");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"blob\"}}"), "zones[0].shape.type");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"rect\",\"from\":[0,0]}}"), "zones[0].shape.to");
    }

    @Test void polygonNeedsThreePoints() {
        ConfigLoader.Result r = parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"polygon\",\"points\":[[0,0],[1,1]]}}");
        assertErrorAt(r, "zones[0].shape.points");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"polygon\",\"points\":[[0,0],[1,1],[1]]}}"),
                "zones[0].shape.points[2]");
    }

    @Test void selfIntersectingPolygonIsOnlyAWarning() {
        ConfigLoader.Result r = parse("{\"id\":\"a\"," + D + ",\"shape\":{\"type\":\"polygon\",\"points\":[[0,0],[10,10],[10,0],[0,10]]}}");
        assertTrue(r.ok());
        assertEquals("zones[0].shape.points", r.warnings().get(0).path());
    }

    @Test void duplicateIds() {
        ConfigLoader.Result r = parse("{\"id\":\"a\"," + D + ",\"default\":true},{\"id\":\"a\",\"dimension\":\"minecraft:the_end\",\"default\":true}");
        assertErrorAt(r, "zones[1].id");
    }

    @Test void missingParent() {
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"parent\":\"nope\"}"), "zones[0].parent");
    }

    @Test void parentCycles() {
        ConfigLoader.Result r = parse("""
            {"id":"a",%1$s,"default":true,"parent":"c"},
            {"id":"b",%1$s,"shape":{"type":"square","center":[0,0],"half":1},"parent":"a"},
            {"id":"c",%1$s,"shape":{"type":"square","center":[0,0],"half":1},"parent":"b"}""".formatted(D));
        assertErrorAt(r, "zones[0].parent");
        assertTrue(r.errors().stream().anyMatch(e -> e.message().contains("цикл")));
        assertEquals(1, r.errors().size(), "one report per cycle");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"parent\":\"a\"}"), "zones[0].parent");
    }

    @Test void dimensionChecks() {
        assertErrorAt(parse("{\"id\":\"a\",\"dimension\":\"Overworld\",\"default\":true}"), "zones[0].dimension");
        ConfigLoader.Result r = ConfigLoader.parse("{\"version\":1,\"zones\":[{\"id\":\"a\",\"dimension\":\"mod:nowhere\",\"default\":true}]}",
                d -> d.startsWith("minecraft:"));
        assertErrorAt(r, "zones[0].dimension");
    }

    @Test void defaultZoneRules() {
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true},{\"id\":\"b\"," + D + ",\"default\":true}"), "zones[1].default");
        assertErrorAt(parse("{\"id\":\"a\"," + D + ",\"default\":true,\"shape\":{\"type\":\"square\",\"center\":[0,0],\"half\":1}}"), "zones[0].shape");
        assertErrorAt(parse("{\"id\":\"a\"," + D + "}"), "zones[0].shape");
    }

    @Test void allErrorsAreCollectedNotJustTheFirst() {
        ConfigLoader.Result r = parse("""
            {"id":"a",%1$s,"priority":"x","shape":{"type":"circle","center":[0],"radius":"r"}}""".formatted(D));
        assertTrue(r.errors().size() >= 3, () -> r.errors().toString());
    }

    @Test void unknownRulesAreWarningsAndUnderscoreCommentIgnored() {
        ConfigLoader.Result r = ConfigLoader.parse("""
            {"_comment":"hi","version":1,"zones":[{"_comment":"x","id":"a","dimension":"minecraft:overworld","default":true,"rules":{"flying":true}}]}""", null);
        assertTrue(r.ok());
        assertEquals("zones[0].rules.flying", r.warnings().get(0).path());
    }
}
