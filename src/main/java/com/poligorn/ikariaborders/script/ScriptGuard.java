package com.poligorn.ikariaborders.script;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Error isolation bookkeeping (TZ §21.7). Pure JVM, time is passed in.
 * Same error is logged at most once a minute; a handler with more than 50 errors within a minute is disabled
 * until {@link #reset()} (KubeJS /reload).
 */
public final class ScriptGuard {
    public static final int MAX_ERRORS_PER_MINUTE = 50;
    public static final long WINDOW_MS = 60_000;

    /** @param log whether this occurrence should be written to the log; @param disabledNow handler was just disabled */
    public record Outcome(boolean log, boolean disabledNow) {}

    private final Map<String, ArrayDeque<Long>> errorTimes = new HashMap<>();
    private final Map<String, Long> lastLog = new HashMap<>();
    private final Map<String, Integer> totalErrors = new HashMap<>();
    private final Set<String> disabled = new HashSet<>();

    /**
     * @param handlerKey identifies one handler (event, target, script source:line)
     * @param signature  identifies "the same error" (event, zone, message)
     */
    public synchronized Outcome recordError(String handlerKey, String signature, long nowMs) {
        totalErrors.merge(handlerKey, 1, Integer::sum);
        ArrayDeque<Long> q = errorTimes.computeIfAbsent(handlerKey, k -> new ArrayDeque<>());
        q.addLast(nowMs);
        while (!q.isEmpty() && nowMs - q.peekFirst() >= WINDOW_MS) q.removeFirst();
        boolean disabledNow = false;
        if (q.size() > MAX_ERRORS_PER_MINUTE && disabled.add(handlerKey)) disabledNow = true;
        Long prev = lastLog.get(signature);
        boolean log = prev == null || nowMs - prev >= WINDOW_MS;
        if (log) {
            lastLog.put(signature, nowMs);
            if (lastLog.size() > 1024) lastLog.values().removeIf(t -> nowMs - t >= WINDOW_MS); // bound memory
        }
        return new Outcome(log, disabledNow);
    }

    public synchronized boolean isDisabled(String handlerKey) {
        return disabled.contains(handlerKey);
    }

    public synchronized Set<String> disabledHandlers() {
        return new TreeSet<>(disabled);
    }

    public synchronized Map<String, Integer> errorTotals() {
        return new HashMap<>(totalErrors);
    }

    /** KubeJS /reload: handlers are recreated, so counters and disabled flags start from scratch. */
    public synchronized void reset() {
        errorTimes.clear();
        lastLog.clear();
        totalErrors.clear();
        disabled.clear();
    }
}
