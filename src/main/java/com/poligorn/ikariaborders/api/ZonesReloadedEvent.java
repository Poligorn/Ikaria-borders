package com.poligorn.ikariaborders.api;

import net.neoforged.bus.api.Event;

/** Fired on {@code NeoForge.EVENT_BUS} after a successful config (re)load. */
public class ZonesReloadedEvent extends Event {
    private final int zoneCount;

    public ZonesReloadedEvent(int zoneCount) {
        this.zoneCount = zoneCount;
    }

    public int getZoneCount() { return zoneCount; }
}
