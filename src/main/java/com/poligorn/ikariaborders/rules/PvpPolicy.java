package com.poligorn.ikariaborders.rules;

/** PvP decision (TZ §6.1). Pure JVM. */
public final class PvpPolicy {
    private PvpPolicy() {}

    /**
     * Whether player→player (or player→pet with {@code pvpPets}) damage must be cancelled.
     * {@code pvpCheck=both} (default, read from the victim's zone): forbidden if at least one of the two zones
     * forbids PvP. {@code victim}: only the victim's zone counts.
     */
    public static boolean denied(ZoneRules attackerZone, ZoneRules victimZone) {
        if (victimZone.pvpForbidden()) return true;
        return "both".equals(victimZone.pvpCheck) && attackerZone.pvpForbidden();
    }

    /** A tamed pet is protected like a player only if the victim zone has {@code pvpPets}. */
    public static boolean petProtected(ZoneRules victimZone) {
        return victimZone.pvpPets;
    }
}
