package com.poligorn.ikariaborders;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.api.PlayerZoneChangeEvent;
import com.poligorn.ikariaborders.api.PlayerZoneChangeEvent.Cause;
import com.poligorn.ikariaborders.api.ZoneView;
import com.poligorn.ikariaborders.zone.Zone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player zone cache, refreshed every {@link #CHECK_INTERVAL} ticks (and immediately on login,
 * dimension change, respawn, teleport and config reload). Fires {@link PlayerZoneChangeEvent}.
 */
public final class PlayerZoneTracker {
    private static final Logger LOG = LogUtils.getLogger();
    public static final int CHECK_INTERVAL = 10;
    private static final long LOG_MIN_GAP_MS = 1000;

    public static final class Entry {
        volatile String dimension;
        volatile ZoneView zone;
        volatile int lastCheckTick;
        volatile boolean dirty;
        volatile Cause pendingCause = Cause.TICK;
        volatile long lastChangeMillis; // 0 = never
        volatile SableCompat.Projection projection;
        long lastLogMillis;

        public ZoneView zone() { return zone; }
        public String dimension() { return dimension; }
        public long lastChangeMillis() { return lastChangeMillis; }
    }

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile boolean debugLog;

    private PlayerZoneTracker() {}

    public static boolean debugLog() { return debugLog; }
    public static void setDebugLog(boolean on) { debugLog = on; }

    public static Entry entry(UUID id) { return ENTRIES.get(id); }

    /** Cached zone if it is fresh (checked < 10 ticks ago, same dimension), else null. */
    public static ZoneView freshZone(ServerPlayer p) {
        Entry e = ENTRIES.get(p.getUUID());
        if (e == null || e.dirty || e.zone == null) return null;
        if (p.server.getTickCount() - e.lastCheckTick >= CHECK_INTERVAL) return null;
        return e.dimension.equals(dimId(p)) ? e.zone : null;
    }

    public static void onLogin(ServerPlayer p) {
        check(p, Cause.LOGIN, true);
    }

    public static void onLogout(ServerPlayer p) {
        ENTRIES.remove(p.getUUID());
    }

    public static void onDimensionChange(ServerPlayer p) {
        check(p, Cause.DIMENSION_CHANGE, false);
    }

    /** Schedule a check at the end of this tick (position is not final yet). */
    public static void markDirty(ServerPlayer p, Cause cause) {
        Entry e = ENTRIES.get(p.getUUID());
        if (e != null) {
            e.pendingCause = cause;
            e.dirty = true;
        }
    }

    public static void invalidateAll() {
        for (Entry e : ENTRIES.values()) {
            e.pendingCause = Cause.RELOAD;
            e.dirty = true;
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Entry e = ENTRIES.get(p.getUUID());
            if (e == null) {
                check(p, Cause.LOGIN, true);
            } else if (e.dirty) {
                check(p, e.pendingCause, false);
            } else if (now - e.lastCheckTick >= CHECK_INTERVAL) {
                check(p, Cause.TICK, false);
            }
        }
    }

    private static String dimId(ServerPlayer p) {
        return p.level().dimension().location().toString();
    }

    private static void check(ServerPlayer p, Cause cause, boolean alwaysFire) {
        SableCompat.Projection proj = SableCompat.project(p);
        String dim = dimId(p);
        Zone z = ZoneManager.config().zoneAt(dim, proj.x(), proj.z());
        Entry e = ENTRIES.computeIfAbsent(p.getUUID(), k -> new Entry());
        ZoneView from = e.zone == null ? ZoneView.NONE : e.zone;
        ZoneView to = z.view();
        boolean changed = e.zone == null || !from.id().equals(to.id());
        e.zone = to;
        e.dimension = dim;
        e.lastCheckTick = p.server.getTickCount();
        e.dirty = false;
        e.pendingCause = Cause.TICK;
        e.projection = proj;
        if (!changed && !alwaysFire) return;
        long nowMs = System.currentTimeMillis();
        e.lastChangeMillis = nowMs;
        if (debugLog && nowMs - e.lastLogMillis >= LOG_MIN_GAP_MS) {
            e.lastLogMillis = nowMs;
            LOG.info("[ikaria_borders/debug] {} ({}): {} -> {} [{}] в {} ({}, {})", p.getGameProfile().getName(),
                    p.getUUID(), from.id(), to.id(), cause, dim, Math.round(proj.x()), Math.round(proj.z()));
        }
        NeoForge.EVENT_BUS.post(new PlayerZoneChangeEvent(p, from, to, cause));
    }
}
