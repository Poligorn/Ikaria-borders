package com.poligorn.ikariaborders.script;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Time budget of {@code zoneTick} + {@code playerTick} (TZ §21.7). Pure JVM, time is passed in.
 * <ul>
 *   <li>average over a 10 s window above 2 ms/tick: a warning ({@link #endTick} returns the average);</li>
 *   <li>a zone that spent more than 10 ms within one tick is skipped until the end of that second.</li>
 * </ul>
 */
public final class ScriptBudget {
    public static final long WARN_AVG_NS = 2_000_000L;
    public static final long SKIP_TICK_NS = 10_000_000L;
    public static final long WINDOW_MS = 10_000L;

    private final Map<String, Long> tickNsByZone = new HashMap<>();
    private final Map<String, Long> skipUntilMs = new HashMap<>();
    private long tickTotalNs;
    private long windowNs;
    private long windowTicks;
    private long windowStartMs = -1;

    /** False while the zone is in its penalty box. */
    public synchronized boolean mayRun(String zoneId, long nowMs) {
        Long until = skipUntilMs.get(zoneId);
        if (until == null) return true;
        if (nowMs >= until) {
            skipUntilMs.remove(zoneId);
            return true;
        }
        return false;
    }

    /** Adds the time one handler call took. Returns true if the zone has just been put into the penalty box. */
    public synchronized boolean record(String zoneId, long ns, long nowMs) {
        tickTotalNs += ns;
        long zoneNs = tickNsByZone.merge(zoneId, ns, Long::sum);
        if (zoneNs > SKIP_TICK_NS && !skipUntilMs.containsKey(zoneId)) {
            skipUntilMs.put(zoneId, (nowMs / 1000 + 1) * 1000); // end of the current second
            return true;
        }
        return false;
    }

    /** Call once per server tick after all handlers ran. Returns the window average (ns/tick) if it exceeded the limit. */
    public synchronized OptionalDouble endTick(long nowMs) {
        if (windowStartMs < 0) windowStartMs = nowMs;
        windowNs += tickTotalNs;
        windowTicks++;
        tickTotalNs = 0;
        tickNsByZone.clear();
        if (nowMs - windowStartMs < WINDOW_MS) return OptionalDouble.empty();
        double avg = (double) windowNs / Math.max(1, windowTicks);
        windowNs = 0;
        windowTicks = 0;
        windowStartMs = nowMs;
        return avg > WARN_AVG_NS ? OptionalDouble.of(avg) : OptionalDouble.empty();
    }

    public synchronized boolean isSkipped(String zoneId, long nowMs) {
        Long until = skipUntilMs.get(zoneId);
        return until != null && nowMs < until;
    }

    public synchronized void reset() {
        tickNsByZone.clear();
        skipUntilMs.clear();
        tickTotalNs = windowNs = windowTicks = 0;
        windowStartMs = -1;
    }
}
