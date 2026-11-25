package com.poligorn.ikariaborders.script;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/** Shared state of the scripting layer (no KubeJS types): bookkeeping, zone data, server thread check. */
public final class ScriptRuntime {
    private static final Logger LOG = LogUtils.getLogger();

    public static final ScriptGuard GUARD = new ScriptGuard();
    public static final ScriptBudget BUDGET = new ScriptBudget();
    public static final ScriptStats STATS = new ScriptStats();
    /** Loaded chunks per zone; maintained only while a script plugin is active. Main thread only. */
    public static final LoadedChunkIndex CHUNKS = new LoadedChunkIndex();
    private static final ZoneDataStore TEMP = new ZoneDataStore();

    private static volatile MinecraftServer server;
    private static ZoneDataStore persistent;
    private static final Map<String, Long> THROTTLE = new HashMap<>();

    private ScriptRuntime() {}

    public static void serverStarting(MinecraftServer s) {
        server = s;
        persistent = null;
    }

    public static void serverStopped() {
        server = null;
        persistent = null;
        TEMP.load(null);
        CHUNKS.clear();
        GUARD.reset();
        BUDGET.reset();
        STATS.reset();
        THROTTLE.clear();
    }

    public static MinecraftServer server() { return server; }

    /** API entry points call this: scripts must stay on the main server thread (TZ §21.7). */
    public static void requireServerThread(String what) {
        MinecraftServer s = server;
        if (s == null) throw new IllegalStateException("[ikaria_borders] " + what + ": сервер ещё не запущен");
        if (!s.isSameThread()) {
            throw new IllegalStateException("[ikaria_borders] " + what + ": API скриптов можно вызывать только из основного потока сервера (сейчас поток '"
                    + Thread.currentThread().getName() + "')");
        }
    }

    /** Persistent zone data (SavedData of the overworld). */
    public static synchronized ZoneDataStore persistentData() {
        if (persistent == null) {
            MinecraftServer s = server;
            if (s == null || s.overworld() == null) throw new IllegalStateException("[ikaria_borders] данные зон недоступны: сервер ещё не запущен");
            DimensionDataStorage storage = s.overworld().getDataStorage();
            persistent = storage.computeIfAbsent(ZoneDataSaved.factory(), ZoneDataSaved.NAME).store;
        }
        return persistent;
    }

    public static ZoneDataStore tempData() { return TEMP; }

    /** Logs a warning at most once a minute per key. */
    public static void warnThrottled(String key, String message) {
        long now = System.currentTimeMillis();
        Long prev = THROTTLE.get(key);
        if (prev != null && now - prev < 60_000) return;
        THROTTLE.put(key, now);
        LOG.warn("[ikaria_borders/script] {}", message);
    }

    /** Warning in the log and to operators online (permission level 2). */
    public static void warnOps(String message) {
        LOG.warn("[ikaria_borders/script] {}", message);
        MinecraftServer s = server;
        if (s == null) return;
        Component c = Component.literal("[ikaria_borders] " + message).withStyle(net.minecraft.ChatFormatting.YELLOW);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) p.sendSystemMessage(c);
        }
    }
}
