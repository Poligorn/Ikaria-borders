package com.poligorn.ikariaborders.opac;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.claims.ClaimEntry;
import com.poligorn.ikariaborders.claims.ClaimRemover;
import com.poligorn.ikariaborders.claims.ClaimSource;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Soft-dependency facade (TZ §7): no OPAC types in signatures, and {@link OpacApi} is only touched
 * when OPAC is loaded. Any linkage problem disables the integration (logged once) instead of crashing.
 */
public final class OpacBridge {
    public static final String OPAC_ID = "openpartiesandclaims";
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean broken;
    private static volatile boolean listenerRegistered;

    private OpacBridge() {}

    public static boolean isLoaded() {
        return !broken && ModList.get() != null && ModList.get().isLoaded(OPAC_ID);
    }

    /** Registers the one-and-only listener; later calls are no-ops. */
    public static synchronized void registerListener(MinecraftServer server) {
        if (!isLoaded()) {
            LOG.info("[ikaria_borders/claims] OPAC не установлен: запрет приватов и чистка отключены");
            return;
        }
        if (listenerRegistered) return;
        try {
            OpacApi.registerListener(server);
            listenerRegistered = true;
            LOG.info("[ikaria_borders/claims] слушатель OPAC зарегистрирован");
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Server stopped: the OPAC listener manager dies with it, so a new server needs a fresh registration. */
    public static void reset() { listenerRegistered = false; }

    public static boolean listenerActive() { return listenerRegistered; }

    public static ClaimSource source(MinecraftServer server) {
        try {
            return isLoaded() ? OpacApi.source(server) : null;
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    public static ClaimRemover remover(MinecraftServer server) {
        try {
            return isLoaded() ? OpacApi.remover(server) : null;
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    /** Server claims of the legacy sub-configs; null if OPAC is unavailable. */
    public static List<ClaimEntry> legacyEntries(MinecraftServer server, List<String> names, Map<String, Integer> perName) {
        try {
            return isLoaded() ? OpacApi.legacyEntries(server, names, perName) : null;
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    private static void fail(Throwable t) {
        broken = true;
        LOG.error("[ikaria_borders/claims] интеграция с OPAC отключена из-за ошибки (несовместимая версия?): {}", t.toString());
    }
}
