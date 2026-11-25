package com.poligorn.ikariaborders.rules;

import static com.poligorn.ikariaborders.rules.RulesTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.zone.ZoneConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DamagePolicyTest {
    private static ZoneRules rules(String damage) {
        ZoneConfig c = config(WILD + "," + square("spawn", 100, 30, null, "{\"pvp\":false,\"damage\":" + damage + "}"));
        return c.zoneAt(OW, 0, 0).rules();
    }

    @Test void exactIdMatches() {
        ZoneRules r = rules("{\"deny\":[\"minecraft:fall\"]}");
        assertTrue(DamagePolicy.denied(r, true, "minecraft:fall", t -> false));
        assertFalse(DamagePolicy.denied(r, true, "minecraft:lava", t -> false));
    }

    @Test void bareIdGetsMinecraftNamespace() {
        ZoneRules r = rules("{\"deny\":[\"fall\"]}");
        assertTrue(DamagePolicy.denied(r, true, "minecraft:fall", t -> false));
    }

    @Test void tagMatchesThroughPredicate() {
        ZoneRules r = rules("{\"deny\":[\"#minecraft:is_explosion\",\"#minecraft:is_fire\"]}");
        Set<String> explosionTypes = Set.of("minecraft:explosion", "minecraft:player_explosion");
        assertTrue(DamagePolicy.denied(r, true, "minecraft:explosion", tag -> tag.equals("minecraft:is_explosion")));
        assertTrue(DamagePolicy.denied(r, true, "minecraft:on_fire", tag -> tag.equals("minecraft:is_fire")));
        assertFalse(DamagePolicy.denied(r, true, "minecraft:cactus", tag -> false));
        assertTrue(explosionTypes.contains("minecraft:explosion"));
    }

    @Test void targetsPlayersByDefaultAllOnRequest() {
        ZoneRules players = rules("{\"deny\":[\"minecraft:fall\"]}");
        assertTrue(DamagePolicy.denied(players, true, "minecraft:fall", t -> false));
        assertFalse(DamagePolicy.denied(players, false, "minecraft:fall", t -> false), "a cow still takes fall damage");
        ZoneRules all = rules("{\"deny\":[\"minecraft:fall\"],\"targets\":\"all\"}");
        assertTrue(DamagePolicy.denied(all, false, "minecraft:fall", t -> false));
    }

    @Test void noRulesNoDenial() {
        ZoneRules wild = config(WILD).zoneAt(OW, 0, 0).rules();
        assertFalse(DamagePolicy.denied(wild, true, "minecraft:fall", t -> true));
    }

    @Test void defaultIkariaConfigKeepsDamageOff() throws Exception {
        String json = new String(PvpPolicyTest.class.getResourceAsStream("/ikaria_borders/zones.example.json").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        ZoneConfig c = com.poligorn.ikariaborders.zone.ConfigLoader.parse(json, null).config();
        assertFalse(c.anyDamageRules());
        assertFalse(c.anyExplosionRules());
        assertFalse(c.anySpawnRules());
        assertFalse(c.anyRemoveHostile());
        assertTrue(c.anyPvpRules(), "only spawn has pvp:false");
        assertTrue(c.zone("spawn").rules().pvpForbidden());
        assertFalse(c.zone("build").rules().pvpForbidden());
    }

    @Test void tagMatchMasks() {
        assertTrue(TagMatch.matches(List.of("hordium:*"), "hordium:husk", null));
        assertFalse(TagMatch.matches(List.of("hordium:*"), "minecraft:husk", null));
        assertTrue(TagMatch.matches(List.of("zombie"), "minecraft:zombie", null));
        assertFalse(TagMatch.matches(List.of("#minecraft:raiders"), "minecraft:zombie", null), "no tag predicate: no match");
    }
}
