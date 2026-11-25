package com.poligorn.ikariaborders.rules;

import static com.poligorn.ikariaborders.rules.RulesTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.zone.ZoneConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplosionPolicyTest {
    private static ZoneConfig cfg() {
        return config(WILD + "," + square("spawn", 100, 30, null, "{\"pvp\":false,\"explosions\":{\"blockDamage\":false,\"entityDamage\":false}}"));
    }

    private record Pos(int x, int z) {}

    @Test void insideKeptOutsideBrokenOnSquareBorder() {
        ZoneConfig c = cfg();
        // a creeper blows up on the border: blocks x = 95..105 at z=0 are in the blast list
        List<Pos> blast = new ArrayList<>();
        for (int x = 95; x <= 105; x++) blast.add(new Pos(x, 0));
        int removed = ExplosionPolicy.removeProtected(blast, p -> ExplosionPolicy.blockProtected(c, OW, p.x(), p.z()));
        // spawn is |x| < 100 by block centre: x = 95..99 protected, 100..105 break
        assertEquals(5, removed);
        assertEquals(List.of(100, 101, 102, 103, 104, 105), blast.stream().map(Pos::x).toList());
    }

    @Test void negativeSideIsSymmetricByBlockCentre() {
        ZoneConfig c = cfg();
        assertTrue(ExplosionPolicy.blockProtected(c, OW, -100, 0), "block -100 has centre -99.5: inside");
        assertFalse(ExplosionPolicy.blockProtected(c, OW, -101, 0));
        assertTrue(ExplosionPolicy.blockProtected(c, OW, 99, 0));
        assertFalse(ExplosionPolicy.blockProtected(c, OW, 100, 0));
        assertTrue(ExplosionPolicy.blockProtected(c, OW, 99, -100));
        assertFalse(ExplosionPolicy.blockProtected(c, OW, 99, 100), "z=100 centre 100.5: outside");
    }

    @Test void entitiesFollowTheirOwnPosition() {
        ZoneConfig c = cfg();
        assertTrue(ExplosionPolicy.entityProtected(c, OW, 50, 50));
        assertFalse(ExplosionPolicy.entityProtected(c, OW, 100.0, 0), "boundary is outside");
        assertFalse(ExplosionPolicy.entityProtected(c, OW, 500, 0));
    }

    @Test void blockAndEntityOptionsAreIndependent() {
        ZoneConfig c = config(WILD + "," + square("s", 100, 30, null, "{\"explosions\":{\"blockDamage\":false}}"));
        assertTrue(ExplosionPolicy.blockProtected(c, OW, 0, 0));
        assertFalse(ExplosionPolicy.entityProtected(c, OW, 0, 0), "entityDamage unset = vanilla");
    }

    @Test void otherDimensionsAreUnaffected() {
        ZoneConfig c = cfg();
        assertFalse(ExplosionPolicy.blockProtected(c, "minecraft:the_nether", 0, 0));
    }
}
