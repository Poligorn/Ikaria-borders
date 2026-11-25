package com.poligorn.ikariaborders.script;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoadedChunkIndexTest {
    private static final String OW = "minecraft:overworld";
    // zone by chunk-centre: "spawn" if |cx|,|cz| <= 6 (≈ half 100), else "wild"
    private static final LoadedChunkIndex.ZoneOfChunk ZONES = (dim, cx, cz) -> Math.abs(cx) <= 6 && Math.abs(cz) <= 6 ? "spawn" : "wild";

    @Test void countsPerZoneAndUnload() {
        LoadedChunkIndex i = new LoadedChunkIndex();
        i.add(OW, 0, 0, ZONES);
        i.add(OW, 1, 1, ZONES);
        i.add(OW, 50, 0, ZONES);
        assertEquals(2, i.count("spawn"));
        assertEquals(1, i.count("wild"));
        i.remove(OW, 0, 0);
        assertEquals(1, i.count("spawn"));
        i.remove(OW, 0, 0); // twice is harmless
        assertEquals(1, i.count("spawn"));
    }

    @Test void negativeCoordinatesRoundTrip() {
        long k = LoadedChunkIndex.key(-5, -7);
        assertEquals(-5, LoadedChunkIndex.keyX(k));
        assertEquals(-7, LoadedChunkIndex.keyZ(k));
    }

    @Test void forEachContinuesWhereItStoppedAndWraps() {
        LoadedChunkIndex i = new LoadedChunkIndex();
        for (int c = 0; c < 10; c++) i.add(OW, c + 100, 0, ZONES); // 10 wild chunks
        Set<Integer> seen = new HashSet<>();
        assertEquals(4, i.forEach("wild", 4, (x, z) -> seen.add(x)));
        assertEquals(4, seen.size());
        i.forEach("wild", 4, (x, z) -> seen.add(x));
        assertEquals(8, seen.size(), "second call continued with new chunks");
        i.forEach("wild", 4, (x, z) -> seen.add(x));
        assertEquals(10, seen.size(), "third call wrapped around");
    }

    @Test void limitCapsAndEmptyZoneIsHarmless() {
        LoadedChunkIndex i = new LoadedChunkIndex();
        i.add(OW, 100, 0, ZONES);
        List<Integer> calls = new ArrayList<>();
        assertEquals(1, i.forEach("wild", 64, (x, z) -> calls.add(x)));
        assertEquals(0, i.forEach("spawn", 64, (x, z) -> fail()));
        assertEquals(0, i.forEach("wild", 0, (x, z) -> fail()));
        assertEquals(0, i.forEach("nope", 5, (x, z) -> fail()));
    }

    @Test void rebuildReassignsAfterConfigReload() {
        LoadedChunkIndex i = new LoadedChunkIndex();
        i.add(OW, 0, 0, ZONES);
        i.add(OW, 10, 0, ZONES);
        assertEquals(1, i.count("spawn"));
        i.rebuild((d, cx, cz) -> "all");
        assertEquals(0, i.count("spawn"));
        assertEquals(2, i.count("all"));
    }

    @Test void removalKeepsTheRestIntact() {
        LoadedChunkIndex i = new LoadedChunkIndex();
        for (int c = 0; c < 5; c++) i.add(OW, c + 100, 0, ZONES);
        i.remove(OW, 101, 0);
        i.remove(OW, 100, 0);
        Set<Integer> seen = new HashSet<>();
        i.forEach("wild", 64, (x, z) -> seen.add(x));
        assertEquals(Set.of(102, 103, 104), seen);
    }
}
