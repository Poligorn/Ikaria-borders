package com.poligorn.ikariaborders;

import net.neoforged.fml.common.Mod;

/** Ikaria Borders — zones and borders (stage 0.1: model, config, commands, API, events). */
@Mod(IkariaBorders.MODID)
public class IkariaBorders {
    public static final String MODID = "ikaria_borders";

    public IkariaBorders() {
        // Everything is wired via @EventBusSubscriber in BordersEvents. No hard dependencies.
    }
}
