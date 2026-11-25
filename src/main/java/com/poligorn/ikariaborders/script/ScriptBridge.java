package com.poligorn.ikariaborders.script;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.api.ClaimActionType;
import com.poligorn.ikariaborders.enforce.ZoneLookup;
import com.poligorn.ikariaborders.zone.Zone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soft-dependency facade for scripting (like OpacBridge). Holds no KubeJS types: the KubeJS plugin registers a
 * {@link ScriptHooks} implementation when KubeJS loads it. Without KubeJS {@link #active()} is false and every
 * method returns immediately. A failing hook never breaks the game event: it is logged and ignored.
 */
public final class ScriptBridge {
    public static final String KUBEJS_ID = "kubejs";
    private static final Logger LOG = LogUtils.getLogger();

    private static volatile ScriptHooks hooks;
    private static volatile boolean readyPending;

    private ScriptBridge() {}

    /** Called by the KubeJS plugin. */
    public static void register(ScriptHooks h) {
        hooks = h;
    }

    public static boolean active() { return hooks != null; }

    public static boolean kubeJsInstalled() {
        return ModList.get() != null && ModList.get().isLoaded(KUBEJS_ID);
    }

    static ScriptHooks hooks() { return hooks; }

    /** {@code ready} fires on the next server tick (once, even if requested several times). */
    public static void requestReady() { readyPending = true; }

    static boolean consumeReady() {
        boolean r = readyPending;
        readyPending = false;
        return r;
    }

    // ---- lifecycle ----

    public static void serverStarting(net.minecraft.server.MinecraftServer s) {
        ScriptRuntime.serverStarting(s);
    }

    public static void serverStarted() {
        if (active()) requestReady();
    }

    public static void serverStopped() {
        readyPending = false;
        ReplaceQueue.clear();
        ScriptRuntime.serverStopped();
    }

    /** Config (re)loaded: reassign loaded chunks to zones; {@code /borders reload} also fires {@code ready}. */
    public static void configLoaded(boolean reload) {
        if (!active()) return;
        ScriptRuntime.CHUNKS.rebuild((dim, cx, cz) -> ZoneManager.config().zoneOfChunk(dim, cx, cz).id());
        if (reload) requestReady();
    }

    // ---- hooks used by always-loaded code ----

    public static void onDamage(LivingIncomingDamageEvent e, Zone zone) {
        ScriptHooks h = hooks;
        if (h == null || !h.has("damage", zone.id())) return;
        try {
            h.onDamage(e, zone);
        } catch (Throwable t) {
            failed("damage", t);
        }
    }

    public static void onMobSpawn(FinalizeSpawnEvent e) {
        ScriptHooks h = hooks;
        if (h == null || ReplaceQueue.isSpawning() || !h.hasAny("mobSpawn")) return;
        try {
            Mob mob = e.getEntity();
            Zone zone = ZoneLookup.of(mob);
            if (!h.has("mobSpawn", zone.id())) return;
            com.poligorn.ikariaborders.SableCompat.Projection p = com.poligorn.ikariaborders.SableCompat.project(mob);
            h.onMobSpawn(e, zone, p.x(), p.z());
        } catch (Throwable t) {
            failed("mobSpawn", t);
        }
    }

    /** @return the message to deny with, or null. Never called for server-claim owners or already denied actions. */
    public static String onClaimAttempt(UUID owner, ServerPlayer player, ClaimActionType action, ChunkPos chunk, Zone zone) {
        ScriptHooks h = hooks;
        if (h == null || !h.has("claimAttempt", zone.id())) return null;
        try {
            return h.onClaimAttempt(owner, player, action, chunk, zone);
        } catch (Throwable t) {
            failed("claimAttempt", t);
            return null;
        }
    }

    static void failed(String what, Throwable t) {
        ScriptRuntime.warnThrottled("hook:" + what, "сбой обработки события '" + what + "' (игровое событие не затронуто): " + t);
    }

    // ---- /borders scripts ----

    public static List<String> report() {
        List<String> out = new ArrayList<>();
        out.add("KubeJS: " + (active() ? "плагин подключён"
                : kubeJsInstalled() ? "установлен, но плагин не загружен" : "не установлен (скриптовые события отключены)"));
        if (!active()) return out;
        out.addAll(ScriptRuntime.STATS.lines());
        Map<String, Integer> errors = ScriptRuntime.GUARD.errorTotals();
        out.add("Ошибки по обработчикам:");
        if (errors.isEmpty()) out.add("  (нет)");
        errors.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> out.add("  " + e.getKey() + ": " + e.getValue()));
        out.add("Отключённые обработчики (до /reload):");
        var disabled = ScriptRuntime.GUARD.disabledHandlers();
        if (disabled.isEmpty()) out.add("  (нет)");
        for (String d : disabled) out.add("  " + d);
        return out;
    }
}
