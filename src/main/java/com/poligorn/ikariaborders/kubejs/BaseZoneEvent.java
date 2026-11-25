package com.poligorn.ikariaborders.kubejs;

import com.poligorn.ikariaborders.script.ZoneJS;
import com.poligorn.ikariaborders.zone.Zone;
import dev.latvian.mods.kubejs.event.KubeEvent;

/** Common part of all zone events: {@code e.zone}. */
abstract class BaseZoneEvent implements KubeEvent, ZoneEventInfo {
    final String eventName;
    final Zone zone;

    BaseZoneEvent(String eventName, Zone zone) {
        this.eventName = eventName;
        this.zone = zone;
    }

    /** The zone the event is about. */
    public ZoneJS getZone() { return ZoneJS.of(zone); }

    @Override public String ikariaEventName() { return eventName; }

    @Override public String ikariaZoneId() { return zone == null ? "-" : zone.id(); }
}
