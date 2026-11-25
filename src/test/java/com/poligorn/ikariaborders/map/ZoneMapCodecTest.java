package com.poligorn.ikariaborders.map;

import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.zone.ConfigLoader;
import com.poligorn.ikariaborders.zone.Shape;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Wire format tests. {@code SyncPayload}'s StreamCodec only wraps these bytes with a bounded byte array
 * (FriendlyByteBuf needs Minecraft classes, which the pure-JVM test classpath does not have), so the format itself is covered here.
 */
class ZoneMapCodecTest {
    static final String OW = "minecraft:overworld";

    @Test
    void exampleConfigRoundTripsAndStaysSmall() throws IOException {
        ZoneMapData d = ZoneMapData.of(ConfigLoader.parse(ZoneMapModelTest.exampleJson(), null).config());
        byte[] b = ZoneMapCodec.encode(d);
        assertTrue(b.length < 2048, "few KB at most, was " + b.length);
        ZoneMapData back = ZoneMapCodec.decode(b);
        assertEquals(d, back);
        assertEquals(6, back.zones().size());
    }

    @Test
    void allShapeTypesRoundTrip() throws IOException {
        Shape poly = new Shape.Polygon(new double[]{0, 10, 10, 0}, new double[]{0, 0, 10, 10});
        List<Shape> shapes = List.of(new Shape.Circle(1, 2, 3), new Shape.Square(0, 0, 100), Shape.Rect.of(0, 0, 5, 5), poly,
                new Shape.Union(List.of(new Shape.Square(0, 0, 5), new Shape.Circle(9, 9, 2))),
                new Shape.Difference(new Shape.Square(0, 0, 1500), List.of(new Shape.Square(0, 0, 100))));
        int i = 0;
        java.util.ArrayList<MapZone> zs = new java.util.ArrayList<>();
        for (Shape s : shapes) {
            zs.add(new MapZone("z" + i, "Зона " + i, OW, s, i, i, 0x123456, i % 2 == 0, true, 0.5f, i % 3 == 0, i % 2 == 0 ? "метка" : null));
            i++;
        }
        zs.add(new MapZone("def", "Дефолт", "minecraft:the_nether", null, 0, 9, 0xFFFFFF, true, false, 1f, false, null));
        ZoneMapData d = ZoneMapCodec.withComputedVersion(zs);
        ZoneMapData back = ZoneMapCodec.decode(ZoneMapCodec.encode(d));
        assertEquals(d.version(), back.version());
        assertEquals(d.zones().size(), back.zones().size());
        for (int k = 0; k < d.zones().size(); k++) {
            MapZone a = d.zones().get(k), c = back.zones().get(k);
            assertEquals(a.id(), c.id());
            assertEquals(a.label(), c.label());
            assertEquals(a.isDefault(), c.isDefault());
            if (a.shape() instanceof Shape.Polygon pa) {
                Shape.Polygon pc = (Shape.Polygon) c.shape();
                assertArrayEquals(pa.xs(), pc.xs());
                assertArrayEquals(pa.zs(), pc.zs());
            } else {
                assertEquals(a.shape(), c.shape());
            }
        }
        // same geometry after the round trip
        MapZone diff = back.zones().get(5);
        assertTrue(diff.contains(500, 0));
        assertFalse(diff.contains(50, 0));
    }

    @Test
    void versionIsStableAndTracksMapRelevantChangesOnly() {
        String a = ZoneMapModelTest.sq("z", 100, 1, "{\"show\":true}", false);
        long v1 = ZoneMapData.of(ZoneMapModelTest.parse(a)).version();
        assertEquals(v1, ZoneMapData.of(ZoneMapModelTest.parse(a)).version());
        assertNotEquals(v1, ZoneMapData.of(ZoneMapModelTest.parse(ZoneMapModelTest.sq("z", 101, 1, "{\"show\":true}", false))).version());
        assertNotEquals(v1, ZoneMapData.of(ZoneMapModelTest.parse(ZoneMapModelTest.sq("z", 100, 1, "{\"show\":true,\"color\":\"#010203\"}", false))).version());
        // rules do not affect the map version
        String withRules = a.substring(0, a.length() - 1) + ",\"rules\":{\"pvp\":false}}";
        assertEquals(v1, ZoneMapData.of(ZoneMapModelTest.parse(withRules)).version());
    }

    @Test
    void emptyDataRoundTrips() throws IOException {
        assertEquals(ZoneMapData.EMPTY, ZoneMapCodec.decode(ZoneMapCodec.encode(ZoneMapData.EMPTY)));
    }

    @Test
    void garbageTruncatedAndHostileInputIsRejectedNotCrashed() {
        byte[] ok = ZoneMapCodec.encode(ZoneMapData.of(ZoneMapModelTest.parse(ZoneMapModelTest.sq("z", 100, 1, "{\"show\":true}", false))));
        assertThrows(IOException.class, () -> ZoneMapCodec.decode(new byte[0]));
        assertThrows(IOException.class, () -> ZoneMapCodec.decode(Arrays.copyOf(ok, ok.length - 3)));
        assertThrows(IOException.class, () -> ZoneMapCodec.decode(Arrays.copyOf(ok, ok.length + 1))); // trailing byte
        byte[] badProto = ok.clone();
        badProto[3] = 99;
        assertThrows(IOException.class, () -> ZoneMapCodec.decode(badProto));
        // absurd zone count: protocol(1) + version + count = Integer.MAX_VALUE
        byte[] huge = new byte[16];
        huge[3] = 1;
        huge[12] = 0x7f; huge[13] = (byte) 0xff; huge[14] = (byte) 0xff; huge[15] = (byte) 0xff;
        assertThrows(IOException.class, () -> ZoneMapCodec.decode(huge));
        assertThrows(IOException.class, () -> ZoneMapCodec.decode(new byte[ZoneMapCodec.MAX_BYTES + 1]));
    }

    @Test
    void absurdlyLongNameIsTruncatedNotFatal() throws IOException {
        String longName = "я".repeat(5000);
        MapZone z = new MapZone("z", longName, OW, null, 0, 0, 0, true, false, 1f, false, null);
        ZoneMapData back = ZoneMapCodec.decode(ZoneMapCodec.encode(ZoneMapCodec.withComputedVersion(List.of(z))));
        assertTrue(back.zones().get(0).name().length() > 100);
        assertTrue(back.zones().get(0).name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 1024);
    }
}
