package com.poligorn.ikariaborders.rules;

import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;

import java.util.List;
import java.util.function.Predicate;

/** {@code explosions.blockDamage} / {@code entityDamage} (TZ §6.2). Pure JVM. */
public final class ExplosionPolicy {
    private ExplosionPolicy() {}

    /** Is the block at integer position (bx, bz) protected from explosions? Block centre decides the zone. */
    public static boolean blockProtected(ZoneConfig cfg, String dimension, int bx, int bz) {
        return blockProtectedAt(cfg, dimension, bx + 0.5, bz + 0.5);
    }

    /** Same for an already computed (e.g. Sable-projected) block centre. */
    public static boolean blockProtectedAt(ZoneConfig cfg, String dimension, double x, double z) {
        Zone zone = cfg.zoneAt(dimension, x, z);
        return Boolean.FALSE.equals(zone.rules().explosionsBlockDamage);
    }

    public static boolean entityProtected(ZoneConfig cfg, String dimension, double x, double z) {
        return Boolean.FALSE.equals(cfg.zoneAt(dimension, x, z).rules().explosionsEntityDamage);
    }

    /** Removes protected elements; returns how many were removed. */
    public static <P> int removeProtected(List<P> affected, Predicate<P> isProtected) {
        int before = affected.size();
        affected.removeIf(isProtected);
        return before - affected.size();
    }
}
