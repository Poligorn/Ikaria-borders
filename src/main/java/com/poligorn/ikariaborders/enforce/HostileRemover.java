package com.poligorn.ikariaborders.enforce;

import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code mobs.removeHostileOnEnter} (TZ §6.3, off by default): every 2 s hostile mobs standing in a zone with the
 * rule are removed. Only loaded entities are looked at. Named, persistent, boss and ridden/riding mobs are kept.
 */
public final class HostileRemover {
    public static final int INTERVAL_TICKS = 40;

    private HostileRemover() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) return;
        ZoneConfig cfg = ZoneManager.config();
        if (!cfg.anyRemoveHostile()) return;
        for (ServerLevel level : server.getAllLevels()) {
            String dim = ZoneLookup.dim(level);
            if (cfg.zones(dim).stream().noneMatch(z -> z.rules().removeHostileOnEnter)) continue;
            List<Mob> doomed = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof Mob m) || m.getType().getCategory() != MobCategory.MONSTER) continue;
                if (m.hasCustomName() || m.isPersistenceRequired() || m.isVehicle() || m.isPassenger()
                        || m.getType().is(Tags.EntityTypes.BOSSES)) continue;
                Zone z = ZoneLookup.of(m);
                if (z.rules().removeHostileOnEnter) doomed.add(m);
            }
            for (Mob m : doomed) {
                DebugLog.log("Враждебный моб убран", () -> m.getType().toShortString() + " в " + dim);
                m.discard();
            }
        }
    }
}
