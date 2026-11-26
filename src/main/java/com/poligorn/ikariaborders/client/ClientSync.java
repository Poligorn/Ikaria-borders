package com.poligorn.ikariaborders.client;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.map.ZoneMapCodec;
import com.poligorn.ikariaborders.map.ZoneMapData;
import com.poligorn.ikariaborders.xaero.XaeroCompat;
import org.slf4j.Logger;

import java.io.IOException;

/** Client handler of {@code ikaria_borders:sync}. Never throws: a bad packet only produces a log line. */
public final class ClientSync {
    private static final Logger LOG = LogUtils.getLogger();

    private ClientSync() {}

    public static void handle(byte[] bytes) {
        ZoneMapData data;
        try {
            data = ZoneMapCodec.decode(bytes);
        } catch (IOException | RuntimeException e) {
            LOG.error("[ikaria_borders/client] Не удалось разобрать данные карты от сервера ({} байт): {}", bytes.length, e.toString());
            return;
        }
        boolean changed = ClientState.MODEL.setData(data);
        LOG.info("[ikaria_borders/client] Получены зоны для карты: {} (версия {}{})", data.zones().size(),
                Long.toHexString(data.version()), changed ? "" : ", без изменений");
        if (changed) XaeroCompat.requestRefresh(); // repaint already baked map regions
    }
}
