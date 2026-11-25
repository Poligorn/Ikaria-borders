package com.poligorn.ikariaborders.opac;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.PlayerZoneTracker;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.api.ClaimActionType;
import com.poligorn.ikariaborders.api.ZoneClaimDeniedEvent;
import com.poligorn.ikariaborders.claims.ClaimEntry;
import com.poligorn.ikariaborders.claims.ClaimPolicy;
import com.poligorn.ikariaborders.claims.ClaimRemover;
import com.poligorn.ikariaborders.claims.ClaimSource;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverrideType;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The ONLY class (with its nested ones) that touches OPAC types. It is loaded solely through {@link OpacBridge},
 * and only when {@code openpartiesandclaims} is installed, so the mod loads fine without OPAC.
 * Signatures verified with {@code javap} against OPAC 0.32.7; behaviour in game is NOT verified.
 */
final class OpacApi {
    private static final Logger LOG = LogUtils.getLogger();

    private OpacApi() {}

    private static IServerClaimsManagerAPI claims(MinecraftServer server) {
        return OpenPACServerAPI.get(server).getServerClaimsManager();
    }

    /** Registers the single listener (it cannot be unregistered; it reads the current config on every call). */
    static void registerListener(MinecraftServer server) {
        if (!SpecialClaimOwners.SERVER.equals(ClaimPolicy.SERVER_OWNER)) {
            LOG.warn("[ikaria_borders/claims] SpecialClaimOwners.SERVER ({}) != ClaimPolicy.SERVER_OWNER ({}): исключение серверных приватов может не работать",
                    SpecialClaimOwners.SERVER, ClaimPolicy.SERVER_OWNER);
        }
        claims(server).getActionListenerManager().register(new Listener());
    }

    private static final class Listener implements IClaimActionListenerAPI {
        @Override
        public String getName() {
            return "ikaria_borders";
        }

        @Override
        public ClaimActionPermissionOverride overrideClaimingActionPermission(UUID owner, ResourceLocation dim, int cx, int cz,
                ClaimingAction action, IServerClaimsManagerAPI manager, ClaimActionPermissionOverride current, MinecraftServer server) {
            try {
                if (current != null && current.getType() == ClaimActionPermissionOverrideType.FORBID) return current;
                ClaimActionType type = ClaimActionType.valueOf(action.name());
                if (!type.restrictable()) return current;
                ZoneConfig cfg = ZoneManager.config(); // always the current config
                String dimId = dim.toString();
                ClaimPolicy.Verdict v = ClaimPolicy.check(cfg, dimId, cx, cz, type, owner);
                if (v.allowed()) return current;
                String msg = ClaimPolicy.formatMessage(cfg.settings().claimDeniedMessage(), v.zone(), dimId, cx, cz, type);
                ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(owner);
                if (PlayerZoneTracker.debugLog()) {
                    LOG.info("[ikaria_borders/claims] отказ {} {} в {} чанк ({}, {}) зона {}", owner, type, dimId, cx, cz, v.zone().id());
                }
                NeoForge.EVENT_BUS.post(new ZoneClaimDeniedEvent(owner, online, dimId, cx, cz, type, v.zone().view()));
                return new ClaimActionPermissionOverride(ClaimActionPermissionOverrideType.FORBID, Component.literal(msg));
            } catch (RuntimeException e) { // never break OPAC claiming because of us
                LOG.error("[ikaria_borders/claims] ошибка в слушателе приватов, действие пропущено: {}", e.toString());
                return current;
            }
        }

        @Override
        public void handleSuccessfulClaimingAction(UUID owner, ResourceLocation dim, int cx, int cz, ClaimingAction action,
                IServerClaimsManagerAPI manager, MinecraftServer server) {
            // nothing to do
        }
    }

    static ClaimSource source(MinecraftServer server) {
        IServerClaimsManagerAPI m = claims(server);
        return new ClaimSource() {
            @Override public List<UUID> owners() {
                return m.getPlayerInfoStream().map(IServerPlayerClaimInfoAPI::getPlayerId).toList();
            }

            @Override public String ownerName(UUID owner) {
                IServerPlayerClaimInfoAPI info = m.getPlayerInfo(owner);
                String n = info == null ? null : info.getPlayerUsername();
                return n == null || n.isEmpty() ? owner.toString() : n;
            }

            @Override public List<ClaimEntry> claimsOf(UUID owner) {
                List<ClaimEntry> out = new ArrayList<>();
                IServerPlayerClaimInfoAPI info = m.getPlayerInfo(owner);
                if (info == null) return out;
                info.getStream().forEach(de -> {
                    String dim = de.getKey().toString();
                    IPlayerDimensionClaimsAPI dc = de.getValue();
                    dc.getStream().forEach(list -> {
                        IPlayerChunkClaimAPI state = list.getClaimState();
                        boolean fl = state.isForceloadable();
                        int sub = state.getSubConfigIndex();
                        list.getStream().forEach(p -> out.add(new ClaimEntry(ClaimEntry.Kind.CLAIM, dim, p.x, p.z, fl, sub)));
                    });
                    for (ChunkPos a : dc.getAnchors()) out.add(ClaimEntry.anchor(dim, a.x, a.z));
                });
                return out;
            }
        };
    }

    static ClaimRemover remover(MinecraftServer server) {
        IServerClaimsManagerAPI m = claims(server);
        return new ClaimRemover() {
            @Override public boolean unclaim(UUID owner, ClaimEntry e) {
                ResourceLocation dim = ResourceLocation.parse(e.dimension());
                IPlayerChunkClaimAPI c = m.get(dim, e.cx(), e.cz());
                if (c == null || !owner.equals(c.getPlayerId())) return false; // stale snapshot
                m.unclaim(dim, e.cx(), e.cz());
                return true;
            }

            @Override public boolean unforceload(UUID owner, ClaimEntry e) {
                ResourceLocation dim = ResourceLocation.parse(e.dimension());
                IPlayerChunkClaimAPI c = m.get(dim, e.cx(), e.cz());
                if (c == null || !owner.equals(c.getPlayerId()) || !c.isForceloadable()) return false;
                // Re-claim in the same sub-config without the forceload flag (not verified in game).
                m.claim(dim, owner, c.getSubConfigIndex(), e.cx(), e.cz(), false);
                return true;
            }

            @Override public boolean removeAnchor(UUID owner, ClaimEntry e) {
                return m.removeAnchor(ResourceLocation.parse(e.dimension()), new ChunkPos(e.cx(), e.cz()), owner);
            }
        };
    }

    /**
     * Server claims in sub-configs whose name matches one of {@code names} (case-insensitive equals or contains,
     * on the sub-config claims name or OPAC's full name). {@code perName} receives chunk counts per matched name.
     */
    static List<ClaimEntry> legacyEntries(MinecraftServer server, List<String> names, Map<String, Integer> perName) {
        IServerClaimsManagerAPI m = claims(server);
        List<ClaimEntry> out = new ArrayList<>();
        IServerPlayerClaimInfoAPI info = m.getPlayerInfo(SpecialClaimOwners.SERVER);
        if (info == null) return out;
        Map<String, Integer> counts = new LinkedHashMap<>();
        info.getStream().forEach(de -> {
            String dim = de.getKey().toString();
            for (IPlayerClaimPosListAPI list : de.getValue().getStream().toList()) {
                IPlayerChunkClaimAPI state = list.getClaimState();
                int sub = state.getSubConfigIndex();
                List<String> candidates = new ArrayList<>();
                String custom = info.getClaimsName(sub);
                if (custom != null) candidates.add(custom);
                try {
                    candidates.add(m.getFullName(state, de.getKey(), false).getString());
                } catch (RuntimeException ex) {
                    // name lookup is best effort
                }
                String matched = null;
                for (String want : names) {
                    for (String c : candidates) {
                        String lc = c.toLowerCase(Locale.ROOT);
                        if (lc.equals(want) || lc.contains(want)) { matched = want; break; }
                    }
                    if (matched != null) break;
                }
                if (matched == null) continue;
                boolean fl = state.isForceloadable();
                int[] n = {0};
                list.getStream().forEach(p -> { out.add(new ClaimEntry(ClaimEntry.Kind.CLAIM, dim, p.x, p.z, fl, sub)); n[0]++; });
                counts.merge(matched, n[0], Integer::sum);
            }
        });
        perName.putAll(counts);
        return out;
    }
}
