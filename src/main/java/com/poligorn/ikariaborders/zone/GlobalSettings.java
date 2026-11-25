package com.poligorn.ikariaborders.zone;

/** Non-rule settings from the {@code global} block of zones.json (TZ §8.2). Pure JVM. */
public record GlobalSettings(String claimDeniedMessage, boolean exemptServerClaims, Sweep sweep) {
    public static final String DEFAULT_CLAIM_DENIED = "В зоне «{zone}» приватить нельзя";

    /** @param intervalMinutes 0 = no periodic sweep */
    public record Sweep(boolean onStart, int intervalMinutes, int perTick, boolean dryRun) {
        public static final Sweep DEFAULT = new Sweep(true, 60, 200, false);
    }

    public static final GlobalSettings DEFAULT = new GlobalSettings(DEFAULT_CLAIM_DENIED, true, Sweep.DEFAULT);
}
