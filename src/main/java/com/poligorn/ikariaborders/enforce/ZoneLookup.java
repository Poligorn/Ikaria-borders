package com.poligorn.ikariaborders.enforce;

import com.poligorn.ikariaborders.SableCompat;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.zone.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Zone lookups for enforcement: exact position, Sable projection first (TZ §5.3). */
public final class ZoneLookup {
    private ZoneLookup() {}

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public static Zone of(Entity e) {
        SableCompat.Projection p = SableCompat.project(e);
        return ZoneManager.config().zoneAt(dim(e.level()), p.x(), p.z());
    }

    public static Zone of(Level level, Vec3 pos) {
        SableCompat.Projection p = SableCompat.project(level, pos);
        return ZoneManager.config().zoneAt(dim(level), p.x(), p.z());
    }

    public static Zone of(Level level, BlockPos pos) {
        return of(level, Vec3.atCenterOf(pos));
    }
}
