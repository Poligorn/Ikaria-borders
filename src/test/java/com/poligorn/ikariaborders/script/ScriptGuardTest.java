package com.poligorn.ikariaborders.script;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScriptGuardTest {
    @Test void sameErrorIsLoggedOncePerMinute() {
        ScriptGuard g = new ScriptGuard();
        assertTrue(g.recordError("h1", "playerTick|wild|boom", 0).log());
        assertFalse(g.recordError("h1", "playerTick|wild|boom", 1_000).log());
        assertFalse(g.recordError("h1", "playerTick|wild|boom", 59_999).log());
        assertTrue(g.recordError("h1", "playerTick|wild|boom", 60_000).log(), "a minute later: again");
        assertTrue(g.recordError("h1", "playerTick|wild|other", 61_000).log(), "a different error is logged at once");
    }

    @Test void disablesAfterMoreThanFiftyErrorsInAMinute() {
        ScriptGuard g = new ScriptGuard();
        for (int i = 0; i < 50; i++) {
            assertFalse(g.recordError("h", "s", i * 100L).disabledNow(), "error " + (i + 1));
        }
        assertFalse(g.isDisabled("h"), "50 is still allowed");
        ScriptGuard.Outcome o = g.recordError("h", "s", 5_000);
        assertTrue(o.disabledNow(), "the 51st error disables");
        assertTrue(g.isDisabled("h"));
        assertFalse(g.recordError("h", "s", 5_100).disabledNow(), "reported once");
        assertTrue(g.disabledHandlers().contains("h"));
    }

    @Test void slowErrorsDoNotDisable() {
        ScriptGuard g = new ScriptGuard();
        for (int i = 0; i < 500; i++) g.recordError("h", "s" + i, i * 2_000L); // 30 per minute
        assertFalse(g.isDisabled("h"));
    }

    @Test void handlersAreCountedIndependently() {
        ScriptGuard g = new ScriptGuard();
        for (int i = 0; i < 60; i++) g.recordError("bad", "s", i);
        g.recordError("good", "s", 0);
        assertTrue(g.isDisabled("bad"));
        assertFalse(g.isDisabled("good"));
    }

    @Test void resetModelsKubeJsReload() {
        ScriptGuard g = new ScriptGuard();
        for (int i = 0; i < 60; i++) g.recordError("bad", "s", i);
        assertTrue(g.isDisabled("bad"));
        g.reset();
        assertFalse(g.isDisabled("bad"));
        assertTrue(g.errorTotals().isEmpty());
        assertTrue(g.recordError("bad", "s", 100).log(), "log throttle also restarts");
    }

    @Test void totalsForReport() {
        ScriptGuard g = new ScriptGuard();
        g.recordError("a", "x", 0);
        g.recordError("a", "y", 10);
        assertEquals(2, g.errorTotals().get("a"));
    }
}
