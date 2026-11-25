package com.poligorn.ikariaborders.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event group {@code IkariaBordersEvents} (TZ §21.1–21.2). Server scripts only. Zone events accept the zone id
 * as target: {@code IkariaBordersEvents.playerEnter('wild', e => ...)}; without a target the handler gets all zones.
 */
final class IkariaEvents {
    static final EventGroup GROUP = EventGroup.of("IkariaBordersEvents");
    static final Map<String, Poster> POSTERS = new LinkedHashMap<>();

    static final Poster READY = poster(GROUP.server("ready", () -> Events.Ready.class), "ready");

    static final Poster PLAYER_ENTER = zone("playerEnter", Events.PlayerZone.class, false);
    static final Poster PLAYER_LEAVE = zone("playerLeave", Events.PlayerZone.class, false);
    static final Poster PLAYER_TICK = zone("playerTick", Events.PlayerTick.class, false);
    static final Poster ZONE_TICK = zone("zoneTick", Events.ZoneTick.class, false);
    static final Poster CHUNK_LOAD = zone("chunkLoad", Events.ChunkLoad.class, false);
    static final Poster MOB_SPAWN = zone("mobSpawn", Events.MobSpawn.class, true);
    static final Poster BLOCK_BREAK = zone("blockBreak", Events.BlockAction.class, true);
    static final Poster BLOCK_PLACE = zone("blockPlace", Events.BlockAction.class, true);
    static final Poster DAMAGE = zone("damage", Events.Damage.class, true);
    static final Poster CLAIM_ATTEMPT = zone("claimAttempt", Events.ClaimAttempt.class, false);
    static final Poster DYNAMIC_ZONE_EXPIRED = zone("dynamicZoneExpired", Events.DynamicZoneExpired.class, false);

    private IkariaEvents() {}

    private static Poster poster(EventHandler h, String name) {
        h.exceptionHandler(Dispatcher::onHandlerError);
        Poster p = new Poster(name, h);
        POSTERS.put(name, p);
        return p;
    }

    /** A zone-targeted event (target = zone id). {@code cancellable}: handlers may call {@code e.cancel()}. */
    private static Poster zone(String name, Class<? extends dev.latvian.mods.kubejs.event.KubeEvent> type, boolean cancellable) {
        TargetedEventHandler<String> t = GROUP.server(name, () -> type).supportsTarget(EventTargetType.STRING);
        if (cancellable) t = t.hasResult();
        t.exceptionHandler(Dispatcher::onHandlerError);
        Poster p = new Poster(name, t);
        POSTERS.put(name, p);
        return p;
    }

    /** Touching this class registers the group; returns it for the plugin. */
    static EventGroup group() { return GROUP; }
}
