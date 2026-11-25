package com.poligorn.ikariaborders.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Per event and per zone handler time statistics for {@code /borders scripts}. Pure JVM. */
public final class ScriptStats {
    public static final class Counter {
        public long calls, totalNs, maxNs, errors;
    }

    private final Map<String, Counter> byEvent = new TreeMap<>();
    private final Map<String, Counter> byZone = new TreeMap<>();

    public synchronized void record(String event, String zone, long ns, boolean error) {
        add(byEvent, event, ns, error);
        add(byZone, zone, ns, error);
    }

    private static void add(Map<String, Counter> m, String key, long ns, boolean error) {
        Counter c = m.computeIfAbsent(key, k -> new Counter());
        c.calls++;
        c.totalNs += ns;
        c.maxNs = Math.max(c.maxNs, ns);
        if (error) c.errors++;
    }

    public synchronized List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("По событиям:");
        if (byEvent.isEmpty()) out.add("  (вызовов не было)");
        byEvent.forEach((k, c) -> out.add("  " + format(k, c)));
        out.add("По зонам:");
        if (byZone.isEmpty()) out.add("  (вызовов не было)");
        byZone.forEach((k, c) -> out.add("  " + format(k, c)));
        return out;
    }

    private static String format(String name, Counter c) {
        double totalMs = c.totalNs / 1e6;
        double avgUs = c.calls == 0 ? 0 : c.totalNs / 1e3 / c.calls;
        return String.format(Locale.ROOT, "%s: вызовов %d, всего %.2f мс, среднее %.1f мкс, макс %.2f мс, ошибок %d",
                name, c.calls, totalMs, avgUs, c.maxNs / 1e6, c.errors);
    }

    public synchronized void reset() {
        byEvent.clear();
        byZone.clear();
    }
}
