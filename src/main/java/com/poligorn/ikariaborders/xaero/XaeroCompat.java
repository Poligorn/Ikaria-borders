package com.poligorn.ikariaborders.xaero;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Asks Xaero to repaint already baked regions after the zone data or client settings changed (TZ §11).
 * Safe without Xaero: classes of each map are only touched if its mod is loaded, and every failure is swallowed.
 */
public final class XaeroCompat {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean warned;

    private XaeroCompat() {}

    public static boolean worldMapLoaded() {
        ModList ml = ModList.get();
        return ml != null && ml.isLoaded("xaeroworldmap");
    }

    public static boolean minimapLoaded() {
        ModList ml = ModList.get();
        return ml != null && ml.isLoaded("xaerominimap");
    }

    /** Client thread only. */
    public static void requestRefresh() {
        if (worldMapLoaded()) {
            try {
                Refresh.worldMap();
            } catch (Throwable t) {
                warn("World Map", t);
            }
        }
        if (minimapLoaded()) {
            try {
                Refresh.minimap();
            } catch (Throwable t) {
                warn("миникарта", t);
            }
        }
    }

    private static void warn(String what, Throwable t) {
        if (warned) return;
        warned = true;
        LOG.warn("[ikaria_borders/xaero] Не удалось попросить Xaero ({}) перерисовать границы: {}", what, t.toString());
    }

    /** Isolated so Xaero types are linked only when a map mod is present. */
    private static final class Refresh {
        static void worldMap() {
            var session = xaero.map.WorldMapSession.getCurrentSession();
            if (session == null || session.getMapProcessor() == null) return;
            var world = session.getMapProcessor().getMapWorld();
            if (world != null) world.clearAllCachedHighlightHashes();
        }

        static void minimap() {
            var session = xaero.common.XaeroMinimapSession.getCurrentSession();
            if (session == null || session.getMinimapProcessor() == null) return;
            var writer = session.getMinimapProcessor().getMinimapWriter();
            if (writer == null) return;
            var handler = writer.getDimensionHighlightHandler();
            if (handler != null) handler.requestRefresh();
        }
    }
}
