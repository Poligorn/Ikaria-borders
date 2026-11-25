package com.poligorn.ikariaborders.kubejs;

import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventHandlerContainer;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One KubeJS event handler plus a per-zone cache of its listeners. KubeJS' own {@code post} cannot skip single
 * handlers, so each listener is wrapped in a stand-alone container and run through {@link Dispatcher}, which
 * adds error isolation, disabling and timing (TZ §21.7).
 */
final class Poster {
    /** One script listener for one target. {@code key} identifies it in counters and the disabled set. */
    record Entry(EventHandlerContainer single, String key) {}

    final String name;
    final EventHandler handler;
    private final Map<String, List<Entry>> cache = new HashMap<>();
    private int generation = -1;

    Poster(String name, EventHandler handler) {
        this.name = name;
        this.handler = handler;
    }

    /** Any listener registered at all (server scripts). */
    boolean hasAny() {
        return handler.hasListeners();
    }

    /** Listeners that apply to this zone: targeted at it, or untargeted. Cached until the next KubeJS reload. */
    List<Entry> entriesFor(String zoneId) {
        if (generation != Dispatcher.generation()) {
            cache.clear();
            generation = Dispatcher.generation();
        }
        List<Entry> l = cache.get(zoneId);
        if (l == null) {
            l = build(zoneId);
            cache.put(zoneId, l);
        }
        return l;
    }

    private List<Entry> build(String zoneId) {
        List<Entry> out = new ArrayList<>();
        handler.forEachListener(ScriptType.SERVER, c -> {
            if (c.target == null || zoneId.equals(String.valueOf(c.target))) {
                // a fresh container has no "child": it runs exactly this one listener
                EventHandlerContainer single = new EventHandlerContainer(c.target, c.handler, c.source, c.line);
                out.add(new Entry(single, Dispatcher.key(name, c)));
            }
        });
        return List.copyOf(out);
    }
}
