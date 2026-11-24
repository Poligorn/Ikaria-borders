package com.poligorn.ikariaborders.zone;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneConfigTest {
    static final String OW = "minecraft:overworld";

    static ZoneConfig example() throws IOException {
        String json = new String(ZoneConfigTest.class.getResourceAsStream("/ikaria_borders/zones.example.json").readAllBytes(), StandardCharsets.UTF_8);
        ConfigLoader.Result r = ConfigLoader.parse(json, null);
        assertTrue(r.ok(), () -> "example must be valid: " + r.errors());
        return r.config();
    }

    @Test void exampleLookups() throws IOException {
        ZoneConfig c = example();
        assertEquals("spawn", c.zoneAt(OW, 0, 0).id());
        assertEquals("spawn", c.zoneAt(OW, 99, -99).id());
        assertEquals("build", c.zoneAt(OW, 100, 0).id());
        assertEquals("build", c.zoneAt(OW, 1400, 1400).id(), "corner of the square is inside build");
        assertEquals("wild", c.zoneAt(OW, 1500, 0).id());
        // "edge" is map-only: never the current zone, wild wins at 5000
        assertEquals("wild", c.zoneAt(OW, 5000, 5000).id());
        assertEquals("nether", c.zoneAt("minecraft:the_nether", 3, 3).id());
        assertEquals("end", c.zoneAt("minecraft:the_end", 3, 3).id());
    }

    @Test void noDefaultYieldsNoneNotNull() throws IOException {
        ZoneConfig c = example();
        Zone z = c.zoneAt("minecraft:unknown_dim", 0, 0);
        assertNotNull(z);
        assertSame(Zone.NONE, z);
        assertEquals("none", z.id());
        assertSame(Zone.NONE, ZoneConfig.EMPTY.zoneAt(OW, 0, 0));
    }

    @Test void inheritanceDownToGlobal() throws IOException {
        ZoneConfig c = example();
        Zone spawn = c.zone("spawn");
        assertEquals(List.of("build", "wild"), spawn.parentChain());
        assertFalse(spawn.ruleBool("pvp", true), "own rule");
        assertTrue(spawn.claimsAllowed(), "from build");
        assertTrue(spawn.ruleBool("claims.forceload", false), "from build");
        assertTrue(c.zone("build").ruleBool("pvp", false), "from wild");
        assertFalse(c.zone("wild").claimsAllowed());
        assertEquals("spawn", spawn.view().meta("hud"));
        assertEquals(Boolean.FALSE, spawn.view().rule("pvp"));
        assertNull(spawn.view().rule("explosions.blockDamage"), "unset => vanilla");
        assertEquals("#55FF55", spawn.view().color());
        assertEquals("build", spawn.view().parentId());
    }

    @Test void globalRulesAreTheRootOfInheritance() {
        String j = """
            {"version":1,"global":{"rules":{"pvp":false,"claims":{"allow":false}}},
             "zones":[{"id":"a","dimension":"minecraft:overworld","default":true,
                       "rules":{"claims":{"allow":true}}}]}""";
        ZoneConfig c = ConfigLoader.parse(j, null).config();
        assertNotNull(c);
        assertFalse(c.zone("a").ruleBool("pvp", true));
        assertTrue(c.zone("a").claimsAllowed());
    }

    @Test void mapOnlyIsExcludedAndHasNoRules() throws IOException {
        ZoneConfig c = example();
        Zone edge = c.zone("edge");
        assertTrue(edge.mapOnly());
        assertEquals(0, edge.resolvedRules().size());
        assertEquals("wild", c.zoneAt(OW, 9000, 0).id());
    }

    @Test void rulesFalseMeansMapOnly() {
        String j = base("""
            {"id":"w","dimension":"minecraft:overworld","default":true,"rules":{"pvp":true}},
            {"id":"line","dimension":"minecraft:overworld","priority":99,"rules":false,
             "shape":{"type":"circle","center":[0,0],"radius":50}}""");
        ZoneConfig c = ConfigLoader.parse(j, null).config();
        assertTrue(c.zone("line").mapOnly());
        assertEquals("w", c.zoneAt(OW, 0, 0).id());
    }

    @Test void priorityHigherWins() {
        String j = base("""
            {"id":"low","dimension":"minecraft:overworld","priority":1,"shape":{"type":"square","center":[0,0],"half":100}},
            {"id":"high","dimension":"minecraft:overworld","priority":7,"shape":{"type":"square","center":[0,0],"half":50}}""");
        ZoneConfig c = ConfigLoader.parse(j, null).config();
        assertEquals("high", c.zoneAt(OW, 0, 0).id());
        assertEquals("low", c.zoneAt(OW, 75, 0).id());
        assertSame(Zone.NONE, c.zoneAt(OW, 500, 0));
    }

    @Test void priorityTieEarlierWinsWithWarning() {
        String j = base("""
            {"id":"first","dimension":"minecraft:overworld","priority":5,"shape":{"type":"square","center":[0,0],"half":100}},
            {"id":"second","dimension":"minecraft:overworld","priority":5,"shape":{"type":"square","center":[0,0],"half":100}}""");
        ConfigLoader.Result r = ConfigLoader.parse(j, null);
        assertTrue(r.ok());
        assertEquals("first", r.config().zoneAt(OW, 0, 0).id());
        assertEquals(1, r.warnings().size());
        assertEquals("zones[1].priority", r.warnings().get(0).path());
    }

    @Test void priorityTieNoWarningWhenDisjoint() {
        String j = base("""
            {"id":"a","dimension":"minecraft:overworld","priority":5,"shape":{"type":"square","center":[0,0],"half":10}},
            {"id":"b","dimension":"minecraft:overworld","priority":5,"shape":{"type":"square","center":[1000,0],"half":10}}""");
        assertTrue(ConfigLoader.parse(j, null).warnings().isEmpty());
    }

    @Test void chunkCenterAtBorder100() throws IOException {
        // centre = cx*16+8; inside iff |centre| < 100  =>  cx in [-6, 5]
        ZoneConfig c = example();
        assertEquals("spawn", c.zoneOfChunk(OW, 5, 0).id());   // 88
        assertEquals("build", c.zoneOfChunk(OW, 6, 0).id());   // 104
        assertEquals("spawn", c.zoneOfChunk(OW, -6, 0).id());  // -88
        assertEquals("build", c.zoneOfChunk(OW, -7, 0).id());  // -104
        assertEquals("spawn", c.zoneOfChunk(OW, 0, 5).id());
        assertEquals("build", c.zoneOfChunk(OW, 0, 6).id());
    }

    @Test void chunkCenterAtBorder1500() throws IOException {
        // 1500/16 = 93.75: centre 93*16+8 = 1496 < 1500 (inside), 94*16+8 = 1512 (outside)
        ZoneConfig c = example();
        assertEquals("build", c.zoneOfChunk(OW, 93, 0).id());
        assertEquals("wild", c.zoneOfChunk(OW, 94, 0).id());
        assertEquals("build", c.zoneOfChunk(OW, -94, 0).id()); // -1496
        assertEquals("wild", c.zoneOfChunk(OW, -95, 0).id());  // -1512
        assertEquals("build", c.zoneOfChunk(OW, 93, 93).id());
        assertEquals("wild", c.zoneOfChunk(OW, 93, 94).id());
        assertFalse(c.zoneOfChunk(OW, 94, 0).claimsAllowed());
        assertTrue(c.zoneOfChunk(OW, 93, 0).claimsAllowed());
    }

    @Test void shapesFromJsonIncludingUnionDifferencePolygonRect() {
        String j = base("""
            {"id":"ring","dimension":"minecraft:overworld","priority":1,"shape":{"type":"difference",
               "base":{"type":"square","center":[0,0],"half":100},
               "minus":[{"type":"circle","center":[0,0],"radius":50}]}},
            {"id":"u","dimension":"minecraft:overworld","priority":2,"shape":{"type":"union","shapes":[
               {"type":"rect","from":[300,300],"to":[310,310]},
               {"type":"polygon","points":[[500,500],[600,500],[600,600]]}]}}""");
        ZoneConfig c = ConfigLoader.parse(j, null).config();
        assertNotNull(c);
        assertSame(Zone.NONE, c.zoneAt(OW, 0, 0));
        assertEquals("ring", c.zoneAt(OW, 70, 0).id());
        assertEquals("u", c.zoneAt(OW, 310.5, 305).id());
        assertEquals("u", c.zoneAt(OW, 590, 510).id());
        assertSame(Zone.NONE, c.zoneAt(OW, 510, 590)); // other half of the square
    }

    private static String base(String zonesJson) {
        return "{\"version\":1,\"zones\":[" + zonesJson + "]}";
    }

    @Test void rootExampleMatchesResource() throws IOException {
        String res = new String(ZoneConfigTest.class.getResourceAsStream("/ikaria_borders/zones.example.json").readAllBytes(), StandardCharsets.UTF_8);
        Path root = Path.of("zones.example.json");
        if (Files.exists(root)) assertEquals(res, Files.readString(root));
    }
}
