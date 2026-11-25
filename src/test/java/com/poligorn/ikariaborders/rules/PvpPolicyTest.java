package com.poligorn.ikariaborders.rules;

import static com.poligorn.ikariaborders.rules.RulesTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.zone.ZoneConfig;
import org.junit.jupiter.api.Test;

class PvpPolicyTest {
    private static ZoneConfig cfg(String spawnRules) {
        return config(WILD + "," + square("spawn", 100, 30, null, spawnRules));
    }

    private static ZoneRules at(ZoneConfig c, double x) {
        return c.zoneAt(OW, x, 0).rules();
    }

    @Test void defaultIkariaConfigOnlySpawnForbidsPvp() {
        ZoneConfig c = cfg("{\"pvp\":false}");
        assertTrue(at(c, 0).pvpForbidden());
        assertFalse(at(c, 500).pvpForbidden(), "wild has pvp:true");
    }

    @Test void bothModeForbidsIfAttackerOrVictimInNoPvpZone() {
        ZoneConfig c = cfg("{\"pvp\":false}");
        ZoneRules spawn = at(c, 0), wild = at(c, 500);
        assertTrue(PvpPolicy.denied(spawn, spawn), "both inside");
        assertTrue(PvpPolicy.denied(spawn, wild), "shooter in spawn, target in wild: forbidden by default (both)");
        assertTrue(PvpPolicy.denied(wild, spawn), "shooter in wild, target in spawn");
        assertFalse(PvpPolicy.denied(wild, wild));
    }

    @Test void victimModeOnlyLooksAtVictimZone() {
        // victim's zone decides the mode; pvpCheck is inherited from the victim zone's rules
        ZoneConfig c = config("{\"id\":\"wild\",\"dimension\":\"minecraft:overworld\",\"default\":true,\"priority\":0,\"rules\":{\"pvp\":true,\"pvpCheck\":\"victim\"}},"
                + square("spawn", 100, 30, null, "{\"pvp\":false,\"pvpCheck\":\"victim\"}"));
        ZoneRules spawn = at(c, 0), wild = at(c, 500);
        assertFalse(PvpPolicy.denied(spawn, wild), "shooter in spawn may hit a target in wild");
        assertTrue(PvpPolicy.denied(wild, spawn), "target in spawn is protected");
        assertTrue(PvpPolicy.denied(spawn, spawn));
    }

    @Test void unsetPvpIsVanilla() {
        ZoneConfig c = config(square("a", 100, 10, null, "{}") + "," + "{\"id\":\"w\",\"dimension\":\"minecraft:overworld\",\"default\":true,\"priority\":0}");
        assertFalse(PvpPolicy.denied(at(c, 0), at(c, 500)));
    }

    @Test void petsProtectedOnlyWhenOptionIsOn() {
        assertFalse(at(cfg("{\"pvp\":false}"), 0).pvpPets, "default: off");
        ZoneConfig on = cfg("{\"pvp\":false,\"pvpPets\":true}");
        assertTrue(PvpPolicy.petProtected(at(on, 0)));
        assertFalse(PvpPolicy.petProtected(at(on, 500)), "wild did not enable pvpPets");
    }

    @Test void pvpPetsAndPvpInheritFromParent() {
        ZoneConfig c = config(WILD + "," + square("outer", 1000, 10, null, "{\"pvp\":false,\"pvpPets\":true}")
                + "," + square("inner", 100, 30, "outer", "{}"));
        ZoneRules inner = at(c, 0);
        assertTrue(inner.pvpForbidden());
        assertTrue(inner.pvpPets);
    }
}
