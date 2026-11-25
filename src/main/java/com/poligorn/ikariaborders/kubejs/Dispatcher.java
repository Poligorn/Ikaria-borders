package com.poligorn.ikariaborders.kubejs;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.script.ScriptBudget;
import com.poligorn.ikariaborders.script.ScriptGuard;
import com.poligorn.ikariaborders.script.ScriptRuntime;
import dev.latvian.mods.kubejs.event.EventExit;
import dev.latvian.mods.kubejs.event.EventHandlerContainer;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.slf4j.Logger;

import java.util.List;

/**
 * Runs script listeners with the guarantees of TZ §21.7: a failing handler never breaks the event or the tick,
 * the same error is logged once a minute, a handler with more than 50 errors a minute is disabled until /reload,
 * and tick events are timed against the budget. Main thread only.
 */
final class Dispatcher {
    private static final Logger LOG = LogUtils.getLogger();
    private static int generation;
    private static boolean errored; // set by onHandlerError during the current call

    private Dispatcher() {}

    static int generation() { return generation; }

    /** Listeners changed (KubeJS reload): caches must be rebuilt. */
    static void invalidate() { generation++; }

    static String key(String event, EventHandlerContainer c) {
        return event + "|" + (c.target == null ? "*" : c.target) + "|" + c.source + ":" + c.line;
    }

    /**
     * Posts the event to every listener of the zone. Returns true if some handler cancelled it
     * ({@code e.cancel()}), which also stops the remaining handlers (KubeJS convention).
     *
     * @param budgeted tick events: skipped while the zone is in its penalty box, time counts against the budget
     */
    static boolean post(Poster p, String zoneId, KubeEvent ev, boolean budgeted) {
        List<Poster.Entry> list = p.entriesFor(zoneId);
        if (list.isEmpty()) return false;
        long nowMs = System.currentTimeMillis();
        ScriptBudget budget = ScriptRuntime.BUDGET;
        if (budgeted && !budget.mayRun(zoneId, nowMs)) return false;
        boolean cancelled = false;
        for (Poster.Entry en : list) {
            if (ScriptRuntime.GUARD.isDisabled(en.key())) continue;
            errored = false;
            long t0 = System.nanoTime();
            boolean stop = false;
            try {
                en.single().handle(ScriptType.SERVER.console, p.handler, ev);
            } catch (EventExit exit) {
                cancelled |= exit.result.interruptFalse();
                stop = true;
            } catch (Throwable t) { // handle() normally swallows script errors itself; this is the safety net
                errored = true;
                recordError(p.name, zoneId, en.key(), en.single(), t);
            }
            long ns = System.nanoTime() - t0;
            ScriptRuntime.STATS.record(p.name, zoneId, ns, errored);
            if (budgeted && budget.record(zoneId, ns, System.currentTimeMillis())) {
                ScriptRuntime.warnOps("зона '" + zoneId + "': скрипты заняли больше 10 мс за тик, её вызовы пропускаются до конца секунды");
            }
            if (stop) break;
        }
        return cancelled;
    }

    /** KubeJS {@code EventExceptionHandler}: called with the unwrapped script error; returning null silences KubeJS' own log. */
    static Throwable onHandlerError(KubeEvent event, EventHandlerContainer container, Throwable t) {
        errored = true;
        String name = event instanceof ZoneEventInfo i ? i.ikariaEventName() : "?";
        String zone = event instanceof ZoneEventInfo i ? i.ikariaZoneId() : "-";
        recordError(name, zone, key(name, container), container, t);
        return null;
    }

    private static void recordError(String event, String zone, String handlerKey, EventHandlerContainer c, Throwable t) {
        String msg = String.valueOf(t.getMessage() == null ? t.toString() : t.toString());
        ScriptGuard.Outcome o = ScriptRuntime.GUARD.recordError(handlerKey, event + "|" + zone + "|" + msg, System.currentTimeMillis());
        if (o.log()) LOG.error("[ikaria_borders/script] {} {}: {} ({}:{})", event, zone, msg, c.source, c.line);
        if (o.disabledNow()) {
            ScriptRuntime.warnOps("обработчик " + handlerKey + " отключён: больше " + ScriptGuard.MAX_ERRORS_PER_MINUTE
                    + " ошибок за минуту. Исправьте скрипт и выполните /reload");
        }
    }
}
