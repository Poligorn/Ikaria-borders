package com.poligorn.ikariaborders.kubejs;

/** What the error handler needs to know about an event: its name and zone (for log lines and counters). */
interface ZoneEventInfo {
    String ikariaEventName();

    String ikariaZoneId();
}
