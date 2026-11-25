package com.poligorn.ikariaborders.rules;

import java.util.function.Predicate;

/** {@code spawn.*} rules (TZ §6.3). Pure JVM. */
public final class SpawnPolicy {
    private SpawnPolicy() {}

    /**
     * @param category MobCategory serialized name ({@code monster}, {@code creature}, ...)
     * @param reason   MobSpawnType name ({@code NATURAL}, ...)
     * @param entityId entity type id
     * @param hasTag   tests the entity type against a tag id (without {@code #})
     * @return true if the spawn must be forbidden. Order: reasons gate everything; denyEntities forbids;
     *         allowEntities exempts from a category ban; otherwise the category decides.
     */
    public static boolean denied(ZoneRules zone, String category, String reason, String entityId, Predicate<String> hasTag) {
        if (!zone.hasSpawnRules()) return false;
        if (!zone.spawnReasons.contains(reason)) return false;
        if (!zone.denyEntities.isEmpty() && TagMatch.matches(zone.denyEntities, entityId, hasTag)) return true;
        if (!Boolean.FALSE.equals(zone.spawnCategories.get(category))) return false;
        return zone.allowEntities.isEmpty() || !TagMatch.matches(zone.allowEntities, entityId, hasTag);
    }
}
