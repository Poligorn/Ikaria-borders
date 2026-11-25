package com.poligorn.ikariaborders.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Fired on {@code NeoForge.EVENT_BUS} when a player's zone changes: login, dimension change,
 * teleport, respawn, config reload and the periodic 10-tick check. On login it is always fired
 * ({@code from} is {@link ZoneView#NONE}). Not cancellable.
 */
public class PlayerZoneChangeEvent extends Event {
    public enum Cause { LOGIN, DIMENSION_CHANGE, TELEPORT, RESPAWN, RELOAD, TICK }

    private final ServerPlayer player;
    private final ZoneView from;
    private final ZoneView to;
    private final Cause cause;

    public PlayerZoneChangeEvent(ServerPlayer player, ZoneView from, ZoneView to, Cause cause) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.cause = cause;
    }

    public ServerPlayer getPlayer() { return player; }
    public ZoneView getFrom() { return from; }
    public ZoneView getTo() { return to; }
    public Cause getCause() { return cause; }
}
