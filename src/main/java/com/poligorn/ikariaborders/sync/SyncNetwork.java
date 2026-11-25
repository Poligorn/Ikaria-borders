package com.poligorn.ikariaborders.sync;

import com.poligorn.ikariaborders.client.ClientSync;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the sync channel as OPTIONAL: clients/servers without the mod still connect (TZ §10). */
public final class SyncNetwork {
    public static final String PROTOCOL = "1";

    private SyncNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(PROTOCOL).optional();
        // the handler body lives in the client package and is only linked when a packet actually arrives (client side)
        reg.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, (payload, ctx) -> ClientSync.handle(payload.data()));
    }
}
