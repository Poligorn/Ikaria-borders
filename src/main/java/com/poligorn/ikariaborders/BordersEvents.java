package com.poligorn.ikariaborders;

import com.poligorn.ikariaborders.api.PlayerZoneChangeEvent.Cause;
import com.poligorn.ikariaborders.command.BordersCommand;
import com.poligorn.ikariaborders.enforce.HostileRemover;
import com.poligorn.ikariaborders.opac.OpacBridge;
import com.poligorn.ikariaborders.script.ScriptBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = IkariaBorders.MODID)
public final class BordersEvents {
    private BordersEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        ScriptBridge.serverStarting(e.getServer());
        ZoneManager.loadAtStartup(e.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        OpacBridge.registerListener(e.getServer()); // one listener per run, reads the current config
        ClaimSweeper.onServerStarted(e.getServer());
        ScriptBridge.serverStarted();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        ClaimSweeper.onServerStopped();
        ScriptBridge.serverStopped();
        OpacBridge.reset();
        PlayerZoneTracker.clear();
        ZoneManager.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        BordersCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        PlayerZoneTracker.tick(e.getServer());
        ClaimSweeper.tick(e.getServer());
        HostileRemover.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerZoneTracker.onLogin(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerZoneTracker.onLogout(p);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerZoneTracker.onDimensionChange(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerZoneTracker.markDirty(p, Cause.RESPAWN);
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerZoneTracker.markDirty(p, Cause.TELEPORT);
    }
}
