package com.poligorn.ikariaborders.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Which loaded chunks belong to which zone (by chunk centre), maintained from chunk load/unload events.
 * Backs {@code zoneTick} ("zone has loaded chunks") and {@code forEachLoadedChunk(fn, limit)}, which
 * continues from where the previous call stopped. Main thread only. Pure JVM.
 */
public final class LoadedChunkIndex {
    @FunctionalInterface
    public interface ZoneOfChunk { String zoneId(String dimension, int cx, int cz); }

    private static final class ZoneChunks {
        final List<Long> list = new ArrayList<>();
        final Map<Long, Integer> pos = new HashMap<>();
        int cursor;

        void add(long k) {
            if (pos.containsKey(k)) return;
            pos.put(k, list.size());
            list.add(k);
        }

        void remove(long k) {
            Integer i = pos.remove(k);
            if (i == null) return;
            long last = list.remove(list.size() - 1);
            if (i < list.size()) { // swap-remove keeps O(1)
                list.set(i, last);
                pos.put(last, i);
            }
        }
    }

    private final Map<String, Map<Long, String>> zoneOfChunk = new HashMap<>(); // dim -> chunk -> zone id
    private final Map<String, ZoneChunks> byZone = new HashMap<>();

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int keyX(long k) { return (int) (k >> 32); }
    public static int keyZ(long k) { return (int) k; }

    public void add(String dim, int cx, int cz, ZoneOfChunk zones) {
        String zone = zones.zoneId(dim, cx, cz);
        long k = key(cx, cz);
        String old = zoneOfChunk.computeIfAbsent(dim, d -> new HashMap<>()).put(k, zone);
        if (old != null && !old.equals(zone)) byZone.get(old).remove(k);
        byZone.computeIfAbsent(zone, z -> new ZoneChunks()).add(k);
    }

    public void remove(String dim, int cx, int cz) {
        Map<Long, String> m = zoneOfChunk.get(dim);
        if (m == null) return;
        long k = key(cx, cz);
        String zone = m.remove(k);
        if (zone != null) byZone.get(zone).remove(k);
    }

    /** After a config reload zone ids/shapes may have changed: reassign every loaded chunk. */
    public void rebuild(ZoneOfChunk zones) {
        List<Object[]> all = new ArrayList<>();
        zoneOfChunk.forEach((dim, m) -> m.keySet().forEach(k -> all.add(new Object[]{dim, k})));
        zoneOfChunk.clear();
        byZone.clear();
        for (Object[] a : all) add((String) a[0], keyX((Long) a[1]), keyZ((Long) a[1]), zones);
    }

    public int count(String zoneId) {
        ZoneChunks z = byZone.get(zoneId);
        return z == null ? 0 : z.list.size();
    }

    public void clear() {
        zoneOfChunk.clear();
        byZone.clear();
    }

    /**
     * Calls {@code fn(cx, cz)} for at most {@code limit} loaded chunks of the zone, starting after the chunk
     * where the previous call ended (wraps around). Returns the number of calls.
     */
    public int forEach(String zoneId, int limit, BiConsumer<Integer, Integer> fn) {
        ZoneChunks z = byZone.get(zoneId);
        if (z == null || z.list.isEmpty() || limit <= 0) return 0;
        // snapshot the batch first: the callback must not see a list that changes under it
        int n = Math.min(limit, z.list.size());
        long[] batch = new long[n];
        int c = z.cursor % z.list.size();
        for (int i = 0; i < n; i++) batch[i] = z.list.get((c + i) % z.list.size());
        z.cursor = (c + n) % z.list.size();
        for (long k : batch) fn.accept(keyX(k), keyZ(k));
        return n;
    }
}
