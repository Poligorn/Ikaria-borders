package com.poligorn.ikariaborders.script;

import com.poligorn.ikariaborders.api.ClaimActionType;
import com.poligorn.ikariaborders.api.ZoneView;
import com.poligorn.ikariaborders.zone.Zone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;
import java.util.UUID;

/**
 * What the always-loaded code can ask of the scripting layer. Contains no KubeJS types: the implementation
 * ({@code kubejs.KubeJsHooks}) is registered by the KubeJS plugin, so without KubeJS nothing is loaded.
 * Every method is called on the server thread; implementations must never throw.
 */
public interface ScriptHooks {
    /** Any handler at all for this event name (e.g. "blockBreak")? Cheap pre-check. */
    boolean hasAny(String event);

    /** A handler for this event that applies to this zone (targeted at it, or untargeted)? */
    boolean has(String event, String zoneId);

    void onReady(MinecraftServer server);

    void onZoneChange(ServerPlayer player, ZoneView from, ZoneView to);

    void onPlayerTick(ServerPlayer player, Zone zone, double x, double z);

    void onZoneTick(Zone zone, ServerLevel level, List<ServerPlayer> players);

    void onChunkLoad(ServerLevel level, ChunkPos pos, Zone zone, boolean isNew);

    void onMobSpawn(FinalizeSpawnEvent event, Zone zone, double x, double z);

    void onBlockBreak(BlockEvent.BreakEvent event, Zone zone);

    void onBlockPlace(BlockEvent.EntityPlaceEvent event, ServerPlayer player, Zone zone);

    void onDamage(LivingIncomingDamageEvent event, Zone zone);

    /** @return message to deny the claim action with, or null to leave it allowed */
    String onClaimAttempt(UUID owner, ServerPlayer player, ClaimActionType action, ChunkPos chunk, Zone zone);
}
