package com.poligorn.ikariaborders.claims;

import static org.junit.jupiter.api.Assertions.*;

import com.poligorn.ikariaborders.zone.ConfigLoader;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SweepJobTest {
    static final String OW = "minecraft:overworld";
    static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID OP = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID CLEAN = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    static ZoneConfig example() throws IOException {
        String json = new String(SweepJobTest.class.getResourceAsStream("/ikaria_borders/zones.example.json").readAllBytes(), StandardCharsets.UTF_8);
        return ConfigLoader.parse(json, null).config();
    }

    /** In-memory claim store that is both source and remover. */
    static final class Fake implements ClaimSource, ClaimRemover {
        final Map<UUID, List<ClaimEntry>> data = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();

        Fake add(UUID o, ClaimEntry... es) {
            data.computeIfAbsent(o, k -> new ArrayList<>()).addAll(List.of(es));
            return this;
        }

        @Override public List<UUID> owners() { return new ArrayList<>(data.keySet()); }
        @Override public String ownerName(UUID o) { return "u" + o.getLeastSignificantBits(); }
        @Override public List<ClaimEntry> claimsOf(UUID o) { return new ArrayList<>(data.get(o)); }

        @Override public boolean unclaim(UUID o, ClaimEntry e) {
            calls.add("unclaim " + e.cx() + "," + e.cz());
            return data.get(o).remove(e);
        }
        @Override public boolean unforceload(UUID o, ClaimEntry e) {
            calls.add("unforceload " + e.cx() + "," + e.cz());
            List<ClaimEntry> l = data.get(o);
            int i = l.indexOf(e);
            if (i < 0) return false;
            l.set(i, ClaimEntry.claim(e.dimension(), e.cx(), e.cz(), false));
            return true;
        }
        @Override public boolean removeAnchor(UUID o, ClaimEntry e) {
            calls.add("anchor " + e.cx() + "," + e.cz());
            return data.get(o).remove(e);
        }
    }

    static ClaimEntry c(int cx, int cz) { return ClaimEntry.claim(OW, cx, cz, false); }

    static Fake world() {
        return new Fake()
                .add(ALICE, c(10, 10), c(200, 200), c(300, -300), c(94, 0))            // 3 in the wild
                .add(OP, c(150, 150), ClaimEntry.claim(OW, 151, 150, true))            // operator: not exempt
                .add(CLEAN, c(1, 1), c(93, 93))                                        // all legal
                .add(ClaimPolicy.SERVER_OWNER, c(500, 500), c(-500, 7));               // server claims: exempt
    }

    static SweepJob run(Fake f, ZoneConfig cfg, boolean apply, int perTick, List<SweepJob.OwnerResult> done) {
        SweepJob j = new SweepJob(f, f, cfg, apply, perTick, 0, done::add);
        int guard = 0;
        while (!j.step(System::nanoTime)) assertTrue(++guard < 1000);
        return j;
    }

    @Test void scanCountsByOwnerAndRemovesNothing() throws IOException {
        Fake f = world();
        SweepJob j = run(f, example(), false, 200, new ArrayList<>());
        assertEquals(3 + 2, j.totalViolations());
        assertEquals(3, j.results().get(ALICE).violations);
        assertEquals(2, j.results().get(OP).violations);
        assertFalse(j.results().containsKey(CLEAN));
        assertFalse(j.results().containsKey(ClaimPolicy.SERVER_OWNER), "server claims are never reported");
        assertEquals(1, j.exemptOwners());
        assertEquals(0, j.totalRemoved());
        assertTrue(f.calls.isEmpty(), "scan must not touch the store");
        assertEquals(4, f.data.get(ALICE).size());
    }

    @Test void dryRunDoesNotDelete() throws IOException {
        Fake f = world();
        // dryRun == apply=false (the manager passes apply = !dryRun for automatic sweeps)
        SweepJob j = run(f, example(), false, 200, new ArrayList<>());
        assertEquals(5, j.totalViolations());
        assertTrue(f.calls.isEmpty());
        assertEquals(2, f.data.get(OP).size());
    }

    @Test void sweepRemovesOperatorAndPlayerButNotServerClaims() throws IOException {
        Fake f = world();
        List<SweepJob.OwnerResult> done = new ArrayList<>();
        SweepJob j = run(f, example(), true, 200, done);
        assertEquals(5, j.totalRemoved());
        assertEquals(List.of(c(10, 10)), f.data.get(ALICE));
        assertEquals(1, f.data.get(ALICE).size(), "only the legal chunk 10,10 is left; 94,0 centre is 1512 -> wild");
        assertEquals(0, f.data.get(OP).size(), "operators are swept like everyone else");
        assertEquals(2, f.data.get(CLEAN).size());
        assertEquals(2, f.data.get(ClaimPolicy.SERVER_OWNER).size());
        assertEquals(2, done.size(), "callback only for owners with violations");
        assertEquals(3, done.get(0).removed);
    }

    @Test void forceloadOnlyViolationKeepsTheClaim() {
        String json = """
            {"version":1,"zones":[{"id":"w","dimension":"minecraft:overworld","default":true,
              "rules":{"claims":{"allow":true,"forceload":false,"anchor":false}}}]}""";
        ZoneConfig cfg = ConfigLoader.parse(json, null).config();
        Fake f = new Fake().add(ALICE, ClaimEntry.claim(OW, 1, 1, true), c(2, 2), ClaimEntry.anchor(OW, 1, 1));
        SweepJob j = run(f, cfg, true, 200, new ArrayList<>());
        assertEquals(2, j.totalRemoved());
        assertEquals(1, j.results().get(ALICE).forceloads);
        assertEquals(1, j.results().get(ALICE).anchors);
        assertEquals(0, j.results().get(ALICE).claims);
        assertEquals(2, f.data.get(ALICE).size(), "both claims survive");
        assertFalse(f.data.get(ALICE).get(0).forceloaded());
    }

    @Test void batchesAreLimitedPerStep() throws IOException {
        Fake f = new Fake();
        for (int i = 0; i < 25; i++) f.add(ALICE, c(200 + i, 200));
        SweepJob j = new SweepJob(f, f, example(), true, 10, 0, null);
        assertFalse(j.step(System::nanoTime));
        assertEquals(10, j.chunksProcessed());
        assertFalse(j.step(System::nanoTime));
        assertEquals(20, j.chunksProcessed());
        assertEquals(20, f.calls.size());
        j.step(System::nanoTime); // 5 more, then the owner is finished
        j.step(System::nanoTime);
        assertTrue(j.isDone());
        assertEquals(25, j.totalRemoved());
        assertEquals(25, j.chunksProcessed());
    }

    @Test void timeBudgetStopsEarly() throws IOException {
        Fake f = new Fake();
        for (int i = 0; i < 100; i++) f.add(ALICE, c(200 + i, 200));
        long[] t = {0};
        SweepJob j = new SweepJob(f, f, example(), true, 100, 1000, null);
        j.step(() -> t[0] += 400); // clock advances 400ns per read; the budget is checked every 16 chunks
        assertTrue(j.chunksProcessed() < 100, "stopped by the time budget, was " + j.chunksProcessed());
        assertTrue(j.chunksProcessed() >= 16);
    }

    @Test void staleSnapshotIsNotCountedAsRemoved() throws IOException {
        Fake f = new Fake().add(ALICE, c(200, 200));
        ClaimRemover refusing = new ClaimRemover() {
            @Override public boolean unclaim(UUID o, ClaimEntry e) { return false; }
            @Override public boolean unforceload(UUID o, ClaimEntry e) { return false; }
            @Override public boolean removeAnchor(UUID o, ClaimEntry e) { return false; }
        };
        SweepJob j = new SweepJob(f, refusing, example(), true, 10, 0, null);
        while (!j.step(System::nanoTime)) { /* run */ }
        assertEquals(1, j.totalViolations());
        assertEquals(0, j.totalRemoved());
    }

    @Test void emptyConfigRemovesNothing() {
        Fake f = world();
        SweepJob j = run(f, ZoneConfig.EMPTY, true, 200, new ArrayList<>());
        assertEquals(0, j.totalViolations());
        assertTrue(f.calls.isEmpty());
    }

    @Test void applyRequiresRemover() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new SweepJob(world(), null, example(), true, 1, 0, null));
    }

    @Test void legacyCleanupBatchesAndDryRun() {
        Fake f = new Fake();
        List<ClaimEntry> es = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ClaimEntry e = c(i, 0);
            es.add(e);
            f.add(ClaimPolicy.SERVER_OWNER, e);
        }
        LegacyCleanupJob dry = new LegacyCleanupJob(es, f, false, 10, 0);
        while (!dry.step(System::nanoTime)) { /* run */ }
        assertEquals(0, dry.removed());
        assertEquals(25, f.data.get(ClaimPolicy.SERVER_OWNER).size());

        LegacyCleanupJob real = new LegacyCleanupJob(es, f, true, 10, 0);
        assertFalse(real.step(System::nanoTime));
        assertEquals(10, real.removed());
        while (!real.step(System::nanoTime)) { /* run */ }
        assertEquals(25, real.removed());
        assertEquals(0, f.data.get(ClaimPolicy.SERVER_OWNER).size());
    }
}
