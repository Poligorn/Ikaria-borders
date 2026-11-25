package com.poligorn.ikariaborders.sync;

import com.poligorn.ikariaborders.IkariaBorders;
import com.poligorn.ikariaborders.map.ZoneMapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code ikaria_borders:sync} (TZ §10): the map slice of the zone config as an opaque, bounded byte blob.
 * The structure is parsed by the client handler inside try/catch, so a malformed packet never disconnects anyone.
 * Only vanilla network classes here: safe to load on a dedicated server.
 */
public record SyncPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IkariaBorders.MODID, "sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
            ByteBufCodecs.byteArray(ZoneMapCodec.MAX_BYTES).map(SyncPayload::new, SyncPayload::data).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
