package com.poligorn.ikariaborders.api;

import com.poligorn.ikariaborders.PlayerZoneTracker;
import com.poligorn.ikariaborders.SableCompat;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.claims.ClaimPolicy;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public API (TZ §9). Static methods; from KubeJS use {@code Java.loadClass}.
 * Query methods never return null except {@link #zone(String)} for an unknown id.
 */
public final class IkariaBordersAPI {
    private IkariaBordersAPI() {}

    /** Zone at exact coordinates (no ship projection: coordinates are taken as real world coordinates). */
    public static ZoneView zoneAt(Level level, double x, double z) {
        return zoneAt(level.dimension(), x, z);
    }

    public static ZoneView zoneAt(ResourceKey<Level> dimension, double x, double z) {
        return zoneAt(dimension.location().toString(), x, z);
    }

    public static ZoneView zoneAt(String dimension, double x, double z) {
        return ZoneManager.config().zoneAt(dimension, x, z).view();
    }

    /**
     * Zone of an entity. If Sable is installed and the entity is on a ship, the position is first
     * projected out of the sub-level. Players are served from a cache refreshed every 10 ticks.
     */
    public static ZoneView zoneOf(Entity entity) {
        if (entity instanceof ServerPlayer sp) {
            ZoneView cached = PlayerZoneTracker.freshZone(sp);
            if (cached != null) return cached;
        }
        SableCompat.Projection p = SableCompat.project(entity);
        return zoneAt(entity.level(), p.x(), p.z());
    }

    /** Zone by chunk centre (cx*16+8, cz*16+8), as used for claims and the map. */
    public static ZoneView zoneOfChunk(String dimension, int cx, int cz) {
        return ZoneManager.config().zoneOfChunk(dimension, cx, cz).view();
    }

    public static ZoneView zoneOfChunk(ResourceKey<Level> dimension, int cx, int cz) {
        return zoneOfChunk(dimension.location().toString(), cx, cz);
    }

    /**
     * Whether claiming is allowed in the chunk per config rules ({@code claims.allow}, chunk-centre zone).
     * Server claims are exempt: pass OPAC's {@code SpecialClaimOwners.SERVER} (UUID 0-0, also
     * {@link com.poligorn.ikariaborders.claims.ClaimPolicy#SERVER_OWNER}). No operator exemptions. OPAC is not consulted.
     */
    public static boolean isClaimAllowed(String dimension, int cx, int cz, UUID owner) {
        return ClaimPolicy.isClaimAllowed(ZoneManager.config(), dimension, cx, cz, owner);
    }

    public static boolean isClaimAllowed(ResourceKey<Level> dimension, int cx, int cz, UUID owner) {
        return isClaimAllowed(dimension.location().toString(), cx, cz, owner);
    }

    /** All zones of a dimension in config order (including default and map-only ones). */
    public static List<ZoneView> zones(String dimension) {
        ZoneConfig c = ZoneManager.config();
        List<ZoneView> out = new ArrayList<>();
        for (Zone z : c.zones(dimension)) out.add(z.view());
        return out;
    }

    public static List<ZoneView> zones(ResourceKey<Level> dimension) {
        return zones(dimension.location().toString());
    }

    /** Zone by id, or null if there is none. */
    public static ZoneView zone(String id) {
        Zone z = ZoneManager.config().zone(id);
        return z == null ? null : z.view();
    }
}
