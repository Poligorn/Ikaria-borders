package com.poligorn.ikariaborders.zone;

import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.rules.ZoneRules;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** rules.custom flags and rules.scripting (0.3). */
class ScriptConfigValidationTest {
    private static ConfigLoader.Result parse(String rules, java.util.function.Predicate<String> known) {
        return ConfigLoader.parse("{\"version\":1,\"zones\":[{\"id\":\"wild\",\"dimension\":\"minecraft:overworld\",\"default\":true,\"rules\":" + rules + "}]}", null, known);
    }

    @Test void unknownCustomFlagIsOnlyAWarning() {
        ConfigLoader.Result r = parse("{\"custom\":{\"ikaria:danger_mult\":2.0}}", name -> false);
        assertTrue(r.ok(), "unknown flag must not fail validation");
        assertTrue(r.warnings().stream().anyMatch(w -> w.path().contains("custom") && w.path().contains("ikaria:danger_mult")), r.warnings().toString());
    }

    @Test void knownFlagHasNoWarning() {
        ConfigLoader.Result r = parse("{\"custom\":{\"ikaria:danger_mult\":2.0}}", Set.of("ikaria:danger_mult")::contains);
        assertTrue(r.ok());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test void flagCheckIsSkippedWhenScriptsAreNotLoadedYet() {
        ConfigLoader.Result r = parse("{\"custom\":{\"ikaria:x\":true}}", null);
        assertTrue(r.ok());
        assertTrue(r.warnings().isEmpty());
    }

    @Test void customMustBeAnObjectAndValuesNotNull() {
        assertFalse(parse("{\"custom\":[1]}", null).ok());
        ConfigLoader.Result r = parse("{\"custom\":{\"ikaria:x\":null}}", null);
        assertFalse(r.ok());
        assertTrue(r.errors().get(0).path().endsWith("custom.ikaria:x"));
    }

    @Test void flagsInheritLikeRules() {
        ConfigLoader.Result r = ConfigLoader.parse("{\"version\":1,\"zones\":["
                + "{\"id\":\"wild\",\"dimension\":\"minecraft:overworld\",\"default\":true,\"rules\":{\"custom\":{\"ikaria:a\":1,\"ikaria:b\":\"w\"}}},"
                + "{\"id\":\"build\",\"dimension\":\"minecraft:overworld\",\"priority\":20,\"parent\":\"wild\","
                + "\"shape\":{\"type\":\"square\",\"center\":[0,0],\"half\":10},\"rules\":{\"custom\":{\"ikaria:b\":\"own\"}}}]}", null);
        assertTrue(r.ok());
        Zone build = r.config().zone("build");
        assertEquals(1, build.customFlag("ikaria:a").getAsInt(), "inherited from parent");
        assertEquals("own", build.customFlag("ikaria:b").getAsString(), "own value wins");
        assertNull(build.customFlag("ikaria:none"));
    }

    @Test void scriptingIntervalsAreValidatedAndClamped() {
        ConfigLoader.Result bad = parse("{\"scripting\":{\"playerTickInterval\":\"x\"}}", null);
        assertFalse(bad.ok());
        assertTrue(bad.errors().get(0).path().endsWith("scripting.playerTickInterval"));
        ConfigLoader.Result low = parse("{\"scripting\":{\"playerTickInterval\":2,\"zoneTickInterval\":5}}", null);
        assertTrue(low.ok(), "too low is a warning, not an error");
        assertEquals(2, low.warnings().size());
        ZoneRules r = low.config().zone("wild").rules();
        assertEquals(5, r.playerTickInterval, "min 5");
        assertEquals(20, r.zoneTickInterval, "min 20");
        ZoneRules d = parse("{}", null).config().zone("wild").rules();
        assertEquals(20, d.playerTickInterval);
        assertEquals(100, d.zoneTickInterval);
        assertEquals(40, parse("{\"scripting\":{\"playerTickInterval\":40}}", null).config().zone("wild").rules().playerTickInterval);
    }
}
