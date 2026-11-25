package com.poligorn.ikariaborders.kubejs;

import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.api.ClaimActionType;
import com.poligorn.ikariaborders.api.ZoneView;
import com.poligorn.ikariaborders.script.ScriptHooks;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneMetrics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;
import java.util.UUID;

/** {@link ScriptHooks} on top of KubeJS: builds the event objects and posts them through {@link Dispatcher}. */
final class KubeJsHooks implements ScriptHooks {
    @Override public boolean hasAny(String event) {
        Poster p = IkariaEvents.POSTERS.get(event);
        return p != null && p.hasAny();
    }

    @Override public boolean has(String event, String zoneId) {
        Poster p = IkariaEvents.POSTERS.get(event);
        return p != null && !p.entriesFor(zoneId).isEmpty();
    }

    @Override public void onReady(MinecraftServer server) {
        Dispatcher.post(IkariaEvents.READY, "-", new Events.Ready(server), false);
    }

    @Override public void onZoneChange(ServerPlayer player, ZoneView from, ZoneView to) {
        Zone f = from.zone(), t = to.zone();
        if (!from.isNone()) Dispatcher.post(IkariaEvents.PLAYER_LEAVE, f.id(), new Events.PlayerZone("playerLeave", player, f, f, t), false);
        if (!to.isNone()) Dispatcher.post(IkariaEvents.PLAYER_ENTER, t.id(), new Events.PlayerZone("playerEnter", player, t, f, t), false);
    }

    @Override public void onPlayerTick(ServerPlayer player, Zone zone, double x, double z) {
        double depth = ZoneMetrics.depth(ZoneManager.config(), zone, x, z);
        Dispatcher.post(IkariaEvents.PLAYER_TICK, zone.id(), new Events.PlayerTick(player, zone, depth), true);
    }

    @Override public void onZoneTick(Zone zone, ServerLevel level, List<ServerPlayer> players) {
        Dispatcher.post(IkariaEvents.ZONE_TICK, zone.id(), new Events.ZoneTick(zone, level, players), true);
    }

    @Override public void onChunkLoad(ServerLevel level, ChunkPos pos, Zone zone, boolean isNew) {
        Dispatcher.post(IkariaEvents.CHUNK_LOAD, zone.id(), new Events.ChunkLoad(zone, level, pos, isNew), false);
    }

    @Override public void onMobSpawn(FinalizeSpawnEvent e, Zone zone, double x, double z) {
        double depth = ZoneMetrics.depth(ZoneManager.config(), zone, x, z);
        Events.MobSpawn ev = new Events.MobSpawn(e, zone, depth);
        boolean cancelled = Dispatcher.post(IkariaEvents.MOB_SPAWN, zone.id(), ev, false);
        if (cancelled || ev.replaced) e.setSpawnCancelled(true);
    }

    @Override public void onBlockBreak(BlockEvent.BreakEvent e, Zone zone) {
        var ev = new Events.BlockAction("blockBreak", (ServerPlayer) e.getPlayer(), (net.minecraft.world.level.Level) e.getLevel(), e.getPos(), e.getState(), zone);
        if (Dispatcher.post(IkariaEvents.BLOCK_BREAK, zone.id(), ev, false)) e.setCanceled(true);
    }

    @Override public void onBlockPlace(BlockEvent.EntityPlaceEvent e, ServerPlayer player, Zone zone) {
        var ev = new Events.BlockAction("blockPlace", player, (net.minecraft.world.level.Level) e.getLevel(), e.getPos(), e.getPlacedBlock(), zone);
        if (Dispatcher.post(IkariaEvents.BLOCK_PLACE, zone.id(), ev, false)) e.setCanceled(true);
    }

    @Override public void onDamage(LivingIncomingDamageEvent e, Zone zone) {
        var ev = new Events.Damage(e.getEntity(), e.getSource(), e.getAmount(), zone);
        boolean cancelled = Dispatcher.post(IkariaEvents.DAMAGE, zone.id(), ev, false);
        if (cancelled) e.setCanceled(true);
        else if (ev.amountChanged) e.setAmount((float) ev.amountValue());
    }

    @Override public String onClaimAttempt(UUID owner, ServerPlayer player, ClaimActionType action, ChunkPos chunk, Zone zone) {
        var ev = new Events.ClaimAttempt(owner, player, action, chunk, zone);
        Dispatcher.post(IkariaEvents.CLAIM_ATTEMPT, zone.id(), ev, false);
        return ev.denyMessage;
    }
}
