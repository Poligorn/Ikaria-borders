package com.poligorn.ikariaborders.script;

import static org.junit.jupiter.api.Assertions.*;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ScriptBudgetTest {
    private static final long MS = 1_000_000L;

    @Test void cheapCallsNeverTrip() {
        ScriptBudget b = new ScriptBudget();
        long now = 0;
        for (int t = 0; t < 300; t++, now += 50) {
            assertTrue(b.mayRun("wild", now));
            assertFalse(b.record("wild", MS / 2, now));
            assertTrue(b.endTick(now).isEmpty());
        }
    }

    @Test void zoneOverTenMsInOneTickIsSkippedUntilEndOfSecond() {
        ScriptBudget b = new ScriptBudget();
        long now = 12_300;
        assertFalse(b.record("wild", 6 * MS, now));
        assertTrue(b.record("wild", 5 * MS, now), "6 + 5 ms > 10 ms in one tick");
        assertFalse(b.mayRun("wild", 12_400));
        assertFalse(b.mayRun("wild", 12_999));
        assertTrue(b.mayRun("wild", 13_000), "end of the second");
        assertTrue(b.mayRun("build", 12_400), "other zones are not affected");
    }

    @Test void singleLongCallTripsImmediately() {
        ScriptBudget b = new ScriptBudget();
        assertTrue(b.record("wild", 20 * MS, 500)); // the 20 ms "hang" of test 21
        assertTrue(b.isSkipped("wild", 600));
        assertFalse(b.isSkipped("wild", 1000));
    }

    @Test void tickAccumulationResetsEveryTick() {
        ScriptBudget b = new ScriptBudget();
        assertFalse(b.record("wild", 6 * MS, 0));
        b.endTick(0);
        assertFalse(b.record("wild", 6 * MS, 50), "new tick, new budget");
    }

    @Test void averageAboveTwoMsOverTenSecondsWarns() {
        ScriptBudget b = new ScriptBudget();
        OptionalDouble warn = OptionalDouble.empty();
        long now = 0;
        for (int t = 0; t <= 200; t++, now += 50) {
            b.record("wild", 3 * MS, now); // 3 ms every tick, never above 10 ms
            OptionalDouble r = b.endTick(now);
            if (r.isPresent()) warn = r;
        }
        assertTrue(warn.isPresent(), "avg 3 ms > 2 ms");
        assertEquals(3.0, warn.getAsDouble() / MS, 0.1);
    }

    @Test void averageBelowLimitDoesNotWarnEvenWithSpikes() {
        ScriptBudget b = new ScriptBudget();
        long now = 0;
        for (int t = 0; t <= 400; t++, now += 50) {
            if (t % 100 == 0) b.record("wild" + t, 8 * MS, now); // one 8 ms spike per 5 s: avg 0.08 ms
            assertTrue(b.endTick(now).isEmpty());
        }
    }

    @Test void resetClearsPenalties() {
        ScriptBudget b = new ScriptBudget();
        b.record("wild", 20 * MS, 100);
        b.reset();
        assertTrue(b.mayRun("wild", 150));
    }
}
