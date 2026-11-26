package com.poligorn.ikariaborders.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.IkariaBorders;
import com.poligorn.ikariaborders.xaero.XaeroCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Client wiring. Only called when {@code FMLEnvironment.dist == CLIENT}; never loaded on a dedicated server. */
public final class ClientInit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String CATEGORY = "key.categories." + IkariaBorders.MODID;

    private static KeyMapping toggleKey;

    private ClientInit() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "ikaria_borders-client.toml");
        modBus.addListener(ClientInit::onConfig);
        modBus.addListener(ClientInit::onConfigReload);
        modBus.addListener(ClientInit::onKeys);
        NeoForge.EVENT_BUS.addListener(ClientInit::onTick);
        NeoForge.EVENT_BUS.addListener(ClientInit::onLogout);
    }

    private static void onConfig(ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() == ClientConfig.SPEC) applyConfig();
    }

    private static void onConfigReload(ModConfigEvent.Reloading e) {
        if (e.getConfig().getSpec() == ClientConfig.SPEC) applyConfig();
    }

    /** Config events can fire off the main thread (file watcher): hop to the client thread before touching Xaero. */
    private static void applyConfig() {
        Minecraft.getInstance().execute(() -> {
            if (ClientState.setConfigSettings(ClientConfig.snapshot())) XaeroCompat.requestRefresh();
        });
    }

    private static void onKeys(RegisterKeyMappingsEvent e) {
        toggleKey = new KeyMapping("key." + IkariaBorders.MODID + ".toggle", InputConstants.UNKNOWN.getValue(), CATEGORY); // unbound by default
        e.register(toggleKey);
    }

    private static void onTick(ClientTickEvent.Post e) {
        if (toggleKey == null) return;
        boolean toggled = false;
        while (toggleKey.consumeClick()) toggled = !toggled;
        if (!toggled) return;
        boolean now = !ClientState.userEnabled();
        if (ClientState.setUserEnabled(now)) XaeroCompat.requestRefresh();
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("Границы зон на карте: " + (now ? "включены" : "выключены")));
        LOG.info("[ikaria_borders/client] Границы зон на карте: {}", now ? "включены" : "выключены");
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        if (ClientState.clearServerData()) XaeroCompat.requestRefresh();
    }
}
