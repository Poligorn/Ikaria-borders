package com.poligorn.ikariaborders.script;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.IkariaBorders;
import com.poligorn.ikariaborders.PlayerZoneTracker;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.api.PlayerZoneChangeEvent;
import com.poligorn.ikariaborders.enforce.ZoneLookup;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.OptionalDouble;

/** Game events that feed the scripting layer. Everything returns at once when no script plugin is active. */
@EventBusSubscriber(modid = IkariaBorders.MODID)
public final class ScriptEvents {
    private static final Logger LOG = LogUtils.getLogger();

    private ScriptEvents() {}

    @SubscribeEvent
    public static void onZoneChange(PlayerZoneChangeEvent e) {
        ScriptHooks h = ScriptBridge.hooks();
        if (h == null || e.getFrom().id().equals(e.getTo().id())) return;
        try {
            h.onZoneChange(e.getPlayer(), e.getFrom(), e.getTo());
        } catch (Throwable t) {
            ScriptBridge.failed("playerEnter/playerLeave", t);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        ScriptHooks h = ScriptBridge.hooks();
        if (h == null || !(e.getPlayer() instanceof ServerPlayer) || !(e.getLevel() instanceof Level level) || !h.hasAny("blockBreak")) return;
        try {
            Zone z = ZoneLookup.of(level, e.getPos());
            if (h.has("blockBreak", z.id())) h.onBlockBreak(e, z);
        } catch (Throwable t) {
            ScriptBridge.failed("blockBreak", t);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        ScriptHooks h = ScriptBridge.hooks();
        if (h == null || !(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof Level level) || !h.hasAny("blockPlace")) return;
        try {
            Zone z = ZoneLookup.of(level, e.getPos());
            if (h.has("blockPlace", z.id())) h.onBlockPlace(e, sp, z);
        } catch (Throwable t) {
            ScriptBridge.failed("blockPlace", t);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load e) {
        ScriptHooks h = ScriptBridge.hooks();
        if (h == null || !(e.getLevel() instanceof ServerLevel level) || !(e.getChunk() instanceof LevelChunk chunk)) return;
        ChunkPos cp = chunk.getPos();
        String dim = ZoneLookup.dim(level);
        ZoneConfig cfg = ZoneManager.config();
        ScriptRuntime.CHUNKS.add(dim, cp.x, cp.z, (d, cx, cz) -> cfg.zoneOfChunk(d, cx, cz).id());
        try {
            Zone z = cfg.zoneOfChunk(dim, cp.x, cp.z);
            if (h.has("chunkLoad", z.id())) h.onChunkLoad(level, cp, z, e.isNewChunk());
        } catch (Throwable t) {
            ScriptBridge.failed("chunkLoad", t);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload e) {
        if (!ScriptBridge.active() || !(e.getLevel() instanceof ServerLevel level)) return;
        ChunkPos cp = e.getChunk().getPos();
        ScriptRuntime.CHUNKS.remove(ZoneLookup.dim(level), cp.x, cp.z);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        ScriptHooks h = ScriptBridge.hooks();
        if (h == null) return;
        MinecraftServer server = e.getServer();
        try {
            tick(h, server);
        } catch (Throwable t) {
            ScriptBridge.failed("tick", t);
        }
    }

    private static void tick(ScriptHooks h, MinecraftServer server) {
        int tick = server.getTickCount();
        ReplaceQueue.process(tick);
        if (ScriptBridge.consumeReady()) {
            h.onReady(server);
            warnUnknownFlags();
        }
        long now = System.currentTimeMillis();
        ZoneConfig cfg = ZoneManager.config();

        if (h.hasAny("playerTick")) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                PlayerZoneTracker.Entry en = PlayerZoneTracker.entry(p.getUUID());
                if (en == null || en.zone() == null || en.projection() == null) continue;
                Zone z = en.zone().zone();
                int interval = z.rules().playerTickInterval;
                if ((tick + (p.getUUID().hashCode() & 0x7fffffff)) % interval != 0) continue;
                if (!h.has("playerTick", z.id())) continue;
                h.onPlayerTick(p, z, en.projection().x(), en.projection().z());
            }
        }
        if (h.hasAny("zoneTick")) {
            for (Zone z : cfg.allZones()) {
                if (z.mapOnly()) continue;
                if ((tick + z.index()) % z.rules().zoneTickInterval != 0) continue;
                if (!h.has("zoneTick", z.id())) continue;
                var players = PlayerZoneTracker.playersIn(server, z.id());
                if (players.isEmpty() && ScriptRuntime.CHUNKS.count(z.id()) == 0) continue; // nobody and nothing loaded
                ResourceLocation dim = ResourceLocation.tryParse(z.dimension());
                ServerLevel level = dim == null ? null : server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, dim));
                if (level == null) continue;
                h.onZoneTick(z, level, players);
            }
        }
        OptionalDouble avg = ScriptRuntime.BUDGET.endTick(now);
        if (avg.isPresent()) {
            ScriptRuntime.warnOps(String.format(java.util.Locale.ROOT,
                    "zoneTick+playerTick занимают в среднем %.2f мс/тик за 10 с (лимит 2 мс); см. /borders scripts", avg.getAsDouble() / 1e6));
        }
    }

    /** After {@code ready}: custom flags in the config that no script declared (warning only, TZ §21.5). */
    private static void warnUnknownFlags() {
        ZoneConfig cfg = ZoneManager.config();
        java.util.Set<String> seen = new java.util.TreeSet<>();
        collectFlags(cfg.globalRules(), seen);
        for (Zone z : cfg.allZones()) collectFlags(z.ownRules(), seen);
        for (String f : seen) {
            if (!FlagRegistry.isKnown(f)) {
                LOG.warn("[ikaria_borders/script] флаг '{}' есть в конфиге (rules.custom), но не объявлен через IkariaBorders.registerFlag; значение сохранено", f);
            }
        }
    }

    private static void collectFlags(com.google.gson.JsonObject rules, java.util.Set<String> out) {
        var c = rules == null ? null : rules.get("custom");
        if (c != null && c.isJsonObject()) out.addAll(c.getAsJsonObject().keySet());
    }
}
