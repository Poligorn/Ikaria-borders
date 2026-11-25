package com.poligorn.ikariaborders;

import com.poligorn.ikariaborders.client.ClientInit;
import com.poligorn.ikariaborders.sync.SyncNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Ikaria Borders — zones and borders. Server logic is wired via @EventBusSubscriber in BordersEvents. No hard dependencies. */
@Mod(IkariaBorders.MODID)
public class IkariaBorders {
    public static final String MODID = "ikaria_borders";

    public IkariaBorders(IEventBus modBus, ModContainer container) {
        modBus.addListener(SyncNetwork::register); // optional channel, both sides
        if (FMLEnvironment.dist == Dist.CLIENT) ClientInit.init(modBus, container);
    }
}
