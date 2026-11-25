package com.poligorn.ikariaborders.rules;

import static com.poligorn.ikariaborders.rules.RulesTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.zone.ZoneConfig;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpawnPolicyTest {
    private static ZoneRules rules(String spawn) {
        ZoneConfig c = config(WILD + "," + square("spawn", 100, 30, null, "{\"spawn\":" + spawn + "}"));
        return c.zoneAt(OW, 0, 0).rules();
    }

    private static boolean denied(ZoneRules r, String category, String reason, String id) {
        return SpawnPolicy.denied(r, category, reason, id, tag -> false);
    }

    @Test void monsterCategoryForbiddenOthersAllowed() {
        ZoneRules r = rules("{\"categories\":{\"monster\":false,\"creature\":true}}");
        assertTrue(denied(r, "monster", "NATURAL", "minecraft:zombie"));
        assertFalse(denied(r, "creature", "NATURAL", "minecraft:cow"));
        assertFalse(denied(r, "ambient", "NATURAL", "minecraft:bat"), "unlisted category: vanilla");
    }

    @Test void defaultReasonsLeaveSpawnersEggsAndCommandsAlone() {
        ZoneRules r = rules("{\"categories\":{\"monster\":false}}");
        for (String ok : new String[]{"SPAWNER", "SPAWN_EGG", "COMMAND", "TRIAL_SPAWNER", "DISPENSER", "BREEDING", "MOB_SUMMONED"}) {
            assertFalse(denied(r, "monster", ok, "minecraft:zombie"), ok);
        }
        for (String no : new String[]{"NATURAL", "CHUNK_GENERATION", "PATROL", "REINFORCEMENT", "EVENT"}) {
            assertTrue(denied(r, "monster", no, "minecraft:zombie"), no);
        }
    }

    @Test void explicitReasonsReplaceTheDefaults() {
        ZoneRules r = rules("{\"categories\":{\"monster\":false},\"reasons\":[\"spawner\",\"NATURAL\"]}");
        assertTrue(denied(r, "monster", "SPAWNER", "minecraft:zombie"), "listed: now forbidden (case-insensitive)");
        assertTrue(denied(r, "monster", "NATURAL", "minecraft:zombie"));
        assertFalse(denied(r, "monster", "PATROL", "minecraft:pillager"), "not listed any more");
    }

    @Test void denyEntitiesBeatAllowedCategory() {
        ZoneRules r = rules("{\"categories\":{\"creature\":true},\"denyEntities\":[\"hordium:*\",\"minecraft:creeper\",\"#minecraft:raiders\"]}");
        assertTrue(denied(r, "monster", "NATURAL", "hordium:husk"), "mod mask");
        assertTrue(denied(r, "monster", "NATURAL", "minecraft:creeper"));
        assertFalse(denied(r, "monster", "NATURAL", "minecraft:zombie"));
        assertTrue(SpawnPolicy.denied(r, "monster", "PATROL", "minecraft:pillager", tag -> tag.equals("minecraft:raiders")), "tag");
    }

    @Test void allowEntitiesExemptFromCategoryBan() {
        ZoneRules r = rules("{\"categories\":{\"monster\":false},\"allowEntities\":[\"minecraft:zombified_piglin\",\"#c:friendly\"]}");
        assertFalse(denied(r, "monster", "NATURAL", "minecraft:zombified_piglin"));
        assertTrue(denied(r, "monster", "NATURAL", "minecraft:zombie"));
        assertFalse(SpawnPolicy.denied(r, "monster", "NATURAL", "x:y", tag -> tag.equals("c:friendly")));
    }

    @Test void denyWinsOverAllow() {
        ZoneRules r = rules("{\"denyEntities\":[\"minecraft:zombie\"],\"allowEntities\":[\"minecraft:zombie\"]}");
        assertTrue(denied(r, "monster", "NATURAL", "minecraft:zombie"));
    }

    @Test void noSpawnRulesInheritedAsVanilla() {
        ZoneRules wild = config(WILD).zoneAt(OW, 0, 0).rules();
        assertFalse(wild.hasSpawnRules());
        assertFalse(denied(wild, "monster", "NATURAL", "minecraft:zombie"));
        assertEquals(Set.of("NATURAL", "CHUNK_GENERATION", "PATROL", "REINFORCEMENT", "EVENT"), wild.spawnReasons);
    }
}
