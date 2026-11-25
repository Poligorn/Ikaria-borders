package com.poligorn.ikariaborders.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Fired on {@code NeoForge.EVENT_BUS} (server thread) when a claim/forceload/anchor action was refused
 * by zone rules. The mod itself writes nothing to the actionbar (project rule); listeners such as
 * KubeJS may. Not cancellable.
 */
public class ZoneClaimDeniedEvent extends Event {
    private final UUID uuid;
    private final ServerPlayer player; // null if the owner is offline
    private final String dimension;
    private final int chunkX, chunkZ;
    private final ClaimActionType action;
    private final ZoneView zone;

    public ZoneClaimDeniedEvent(UUID uuid, ServerPlayer player, String dimension, int chunkX, int chunkZ,
                                ClaimActionType action, ZoneView zone) {
        this.uuid = uuid;
        this.player = player;
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.action = action;
        this.zone = zone;
    }

    public UUID getUuid() { return uuid; }
    /** The online player, or null. */
    public ServerPlayer getPlayer() { return player; }
    public String getDimension() { return dimension; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public ClaimActionType getAction() { return action; }
    public ZoneView getZone() { return zone; }
}
