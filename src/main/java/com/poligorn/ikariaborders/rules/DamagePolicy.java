package com.poligorn.ikariaborders.rules;

import java.util.function.Predicate;

/** {@code damage.deny} / {@code damage.targets} (TZ §6.2). Pure JVM. */
public final class DamagePolicy {
    private DamagePolicy() {}

    /**
     * @param typeId  damage type id, e.g. {@code minecraft:fall}
     * @param hasTag  tests the damage type against a tag id (without {@code #})
     */
    public static boolean denied(ZoneRules zone, boolean victimIsPlayer, String typeId, Predicate<String> hasTag) {
        if (!zone.hasDamageRules()) return false;
        if (!zone.damageAllTargets && !victimIsPlayer) return false;
        return TagMatch.matches(zone.damageDeny, typeId, hasTag);
    }
}
