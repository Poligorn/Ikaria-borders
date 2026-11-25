package com.poligorn.ikariaborders.api;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/** Fired on {@code NeoForge.EVENT_BUS} (server thread) when a sweep actually removed claims of an owner. */
public class ZoneClaimsSweptEvent extends Event {
    private final UUID owner;
    private final String ownerName;
    private final int count;

    public ZoneClaimsSweptEvent(UUID owner, String ownerName, int count) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.count = count;
    }

    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    /** Number of removed claims / forceloads / anchors. */
    public int getCount() { return count; }
}
