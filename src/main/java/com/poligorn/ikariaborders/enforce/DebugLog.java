package com.poligorn.ikariaborders.enforce;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.PlayerZoneTracker;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Rate-limited debug log of cancelled PvP / damage / explosions / spawns (/borders debug log on). */
public final class DebugLog {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long MIN_GAP_MS = 1000;

    private static final class Slot { long last; int suppressed; }
    private static final Map<String, Slot> SLOTS = new HashMap<>();

    private DebugLog() {}

    /** At most one line per second per category; the number of suppressed lines is appended. Main thread only. */
    public static void log(String category, Supplier<String> message) {
        if (!PlayerZoneTracker.debugLog()) return;
        long now = System.currentTimeMillis();
        Slot s = SLOTS.computeIfAbsent(category, k -> new Slot());
        if (now - s.last < MIN_GAP_MS) {
            s.suppressed++;
            return;
        }
        s.last = now;
        String extra = s.suppressed > 0 ? " (ещё " + s.suppressed + " подавлено)" : "";
        s.suppressed = 0;
        LOG.info("[ikaria_borders/debug] {}: {}{}", category, message.get(), extra);
    }
}
