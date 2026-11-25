package com.poligorn.ikariaborders.xaero;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Entry points called by the mixins. They take {@code Object} so the mixin classes themselves stay free of Xaero types;
 * every failure is caught and logged: the mod then simply runs without map lines (TZ §4.2).
 */
public final class XaeroHooks {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean worldMapFailed, minimapFailed;

    private XaeroHooks() {}

    /** Called at HEAD of {@code xaero.map.highlight.HighlighterRegistry.end()}, before the list is frozen. */
    public static void registerWorldMap(Object registry) {
        try {
            ((xaero.map.highlight.HighlighterRegistry) registry).register(new WorldMapZoneHighlighter());
        } catch (Throwable t) {
            if (!worldMapFailed) {
                worldMapFailed = true;
                LOG.error("[ikaria_borders/xaero] Не удалось зарегистрировать подсветку зон на World Map — работаем без линий на ней. Возможно, версия Xaero не 1.47.0: {}", t.toString());
            }
        }
    }

    /** Called at HEAD of {@code xaero.common.minimap.highlight.HighlighterRegistry.end()}. */
    public static void registerMinimap(Object registry) {
        try {
            ((xaero.common.minimap.highlight.HighlighterRegistry) registry).register(new MinimapZoneHighlighter());
        } catch (Throwable t) {
            if (!minimapFailed) {
                minimapFailed = true;
                LOG.error("[ikaria_borders/xaero] Не удалось зарегистрировать подсветку зон на миникарте — работаем без линий на ней. Возможно, версия Xaero не 26.6.0: {}", t.toString());
            }
        }
    }
}
