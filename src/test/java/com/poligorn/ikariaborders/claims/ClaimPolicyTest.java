package com.poligorn.ikariaborders.claims;

import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.api.ClaimActionType;
import com.poligorn.ikariaborders.zone.ConfigLoader;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimPolicyTest {
    static final String OW = "minecraft:overworld";
    static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    static final UUID OP = UUID.fromString("aaaaaaaa-2222-3333-4444-555555555555");

    static ZoneConfig example() throws IOException {
        String json = new String(ClaimPolicyTest.class.getResourceAsStream("/ikaria_borders/zones.example.json").readAllBytes(), StandardCharsets.UTF_8);
        ConfigLoader.Result r = ConfigLoader.parse(json, null);
        assertTrue(r.ok(), () -> "example must be valid: " + r.errors());
        return r.config();
    }

    static boolean claim(ZoneConfig c, int cx, int cz, UUID owner) {
        return ClaimPolicy.check(c, OW, cx, cz, ClaimActionType.CLAIM, owner).allowed();
    }

    @Test void serverOwnerConstantIsZeroUuid() {
        assertEquals(new UUID(0, 0), ClaimPolicy.SERVER_OWNER);
    }

    @Test void borderAt1500UsesChunkCenter() throws IOException {
        ZoneConfig c = example();
        // 1500/16 = 93.75: chunk 93 spans 1488..1503, centre 1496 < 1500 -> inside build
        assertTrue(claim(c, 93, 0, PLAYER));
        assertTrue(claim(c, -94, 0, PLAYER), "centre -1496 is inside (|dx| < 1500)");
        // chunk 94: centre 1512 -> wild
        assertFalse(claim(c, 94, 0, PLAYER));
        assertFalse(claim(c, -95, 0, PLAYER), "centre -1512");
        assertFalse(claim(c, 0, 94, PLAYER));
        // the chunk straddling the border is decided by its centre, not by its corner
        assertTrue(1488 < 1500 && 1503 > 1500);
    }

    @Test void squareCornerIsAllowedUnlikeTheOldCircle() throws IOException {
        ZoneConfig c = example();
        assertTrue(claim(c, 87, 87, PLAYER), "chunk centre 1400,1400 (test 1)");
        assertTrue(claim(c, 93, 93, PLAYER), "centre 1496,1496 is inside the square");
        assertFalse(claim(c, 94, 94, PLAYER));
    }

    @Test void borderAt100() throws IOException {
        ZoneConfig c = example();
        assertEquals("spawn", c.zoneOfChunk(OW, 5, 0).id());   // centre 88
        assertEquals("build", c.zoneOfChunk(OW, 6, 0).id());   // centre 104
        assertEquals("spawn", c.zoneOfChunk(OW, -6, -6).id()); // centre -88
        assertEquals("build", c.zoneOfChunk(OW, -7, 0).id());  // centre -104
        // spawn is a claim-allowed zone (inherits build)
        assertTrue(claim(c, 0, 0, PLAYER));
        assertTrue(claim(c, 6, 0, PLAYER));
    }

    @Test void wildBuildSpawnRules() throws IOException {
        ZoneConfig c = example();
        for (ClaimActionType a : new ClaimActionType[]{ClaimActionType.CLAIM, ClaimActionType.FORCELOAD, ClaimActionType.ANCHOR}) {
            assertFalse(ClaimPolicy.check(c, OW, 200, 200, a, PLAYER).allowed(), "wild denies " + a);
            assertTrue(ClaimPolicy.check(c, OW, 10, 10, a, PLAYER).allowed(), "build allows " + a);
            assertTrue(ClaimPolicy.check(c, OW, 0, 0, a, PLAYER).allowed(), "spawn allows " + a);
        }
        assertEquals("wild", ClaimPolicy.check(c, OW, 200, 200, ClaimActionType.CLAIM, PLAYER).zone().id());
        // nether / end have no claims rules: vanilla, allowed
        assertTrue(ClaimPolicy.check(c, "minecraft:the_nether", 500, 500, ClaimActionType.CLAIM, PLAYER).allowed());
        assertTrue(ClaimPolicy.check(c, "minecraft:the_end", 500, 500, ClaimActionType.CLAIM, PLAYER).allowed());
    }

    @Test void unActionsAreAlwaysAllowed() throws IOException {
        ZoneConfig c = example();
        for (ClaimActionType a : new ClaimActionType[]{ClaimActionType.UNCLAIM, ClaimActionType.UNFORCELOAD,
                ClaimActionType.UNANCHOR, ClaimActionType.UNCLAIM_UNANCHORED}) {
            assertTrue(ClaimPolicy.check(c, OW, 200, 200, a, PLAYER).allowed(), a.name());
            assertFalse(a.restrictable());
        }
    }

    @Test void onlyServerClaimsAreExempt() throws IOException {
        ZoneConfig c = example();
        assertFalse(claim(c, 200, 200, OP), "an operator is not exempt");
        assertFalse(claim(c, 200, 200, PLAYER));
        assertTrue(claim(c, 200, 200, ClaimPolicy.SERVER_OWNER), "server claims allowed in the wild");
        assertTrue(ClaimPolicy.check(c, OW, 200, 200, ClaimActionType.FORCELOAD, ClaimPolicy.SERVER_OWNER).allowed());
        assertTrue(ClaimPolicy.isExempt(c, ClaimPolicy.SERVER_OWNER));
        assertFalse(ClaimPolicy.isExempt(c, OP));
        assertFalse(ClaimPolicy.isExempt(c, null));
    }

    @Test void exemptFlagCanBeDisabled() {
        String json = """
            {"version":1,"global":{"exempt":{"serverClaims":false}},
             "zones":[{"id":"w","dimension":"minecraft:overworld","default":true,"rules":{"claims":{"allow":false}}}]}""";
        ZoneConfig c = ConfigLoader.parse(json, null).config();
        assertFalse(ClaimPolicy.isClaimAllowed(c, OW, 500, 500, ClaimPolicy.SERVER_OWNER));
    }

    @Test void messageFormatting() throws IOException {
        ZoneConfig c = example();
        var v = ClaimPolicy.check(c, OW, 200, 200, ClaimActionType.CLAIM, PLAYER);
        String msg = ClaimPolicy.formatMessage(c.settings().claimDeniedMessage(), v.zone(), OW, 200, 200, ClaimActionType.CLAIM);
        assertEquals("В зоне «Дикие земли» приватить нельзя", msg);
        assertEquals("wild 200,200 claim", ClaimPolicy.formatMessage("{id} {cx},{cz} {action}", v.zone(), OW, 200, 200, ClaimActionType.CLAIM));
    }

    @Test void emptyConfigDeniesNothing() {
        assertTrue(ClaimPolicy.isClaimAllowed(ZoneConfig.EMPTY, OW, 9999, 9999, PLAYER));
    }

    @Test void forceloadAndAnchorCanBeDeniedSeparately() {
        String json = """
            {"version":1,"zones":[{"id":"w","dimension":"minecraft:overworld","default":true,
              "rules":{"claims":{"allow":true,"forceload":false,"anchor":false}}}]}""";
        ZoneConfig c = ConfigLoader.parse(json, null).config();
        assertTrue(ClaimPolicy.check(c, OW, 1, 1, ClaimActionType.CLAIM, PLAYER).allowed());
        assertFalse(ClaimPolicy.check(c, OW, 1, 1, ClaimActionType.FORCELOAD, PLAYER).allowed());
        assertFalse(ClaimPolicy.check(c, OW, 1, 1, ClaimActionType.ANCHOR, PLAYER).allowed());
    }
}
