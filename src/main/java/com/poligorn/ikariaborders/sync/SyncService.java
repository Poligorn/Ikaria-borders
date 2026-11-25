package com.poligorn.ikariaborders.sync;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.map.ZoneMapCodec;
import com.poligorn.ikariaborders.map.ZoneMapData;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/** Server side of the map sync: login, /borders reload, /borders resync. Main thread only. */
public final class SyncService {
    private static final Logger LOG = LogUtils.getLogger();

    private record Cached(ZoneConfig config, SyncPayload payload, int zones) {}

    private static Cached cached;

    private SyncService() {}

    private static Cached payload() {
        ZoneConfig cfg = ZoneManager.config();
        Cached c = cached;
        if (c != null && c.config == cfg) return c;
        ZoneMapData data = ZoneMapData.of(cfg);
        byte[] bytes = ZoneMapCodec.encode(data);
        if (bytes.length > ZoneMapCodec.MAX_BYTES) {
            LOG.error("[ikaria_borders/sync] Данные карты ({} байт) больше лимита {} — клиентам отправлен пустой набор",
                    bytes.length, ZoneMapCodec.MAX_BYTES);
            data = ZoneMapData.EMPTY;
            bytes = ZoneMapCodec.encode(data);
        }
        cached = c = new Cached(cfg, new SyncPayload(bytes), data.zones().size());
        return c;
    }

    /** Sends the current map data to one player; returns false if the client does not understand the channel. */
    public static boolean send(ServerPlayer player) {
        if (!player.connection.hasChannel(SyncPayload.TYPE)) return false; // vanilla / no mod: stay silent
        Cached c = payload();
        PacketDistributor.sendToPlayer(player, c.payload());
        return true;
    }

    /** After /borders reload: everyone online. Returns how many clients received it. */
    public static int broadcast(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (send(p)) n++;
        LOG.info("[ikaria_borders/sync] Карта зон отправлена клиентам: {} из {} игроков", n, server.getPlayerList().getPlayerCount());
        return n;
    }

    public static int zoneCount() {
        return payload().zones();
    }

    public static int payloadSize() {
        return payload().payload().data().length;
    }
}
