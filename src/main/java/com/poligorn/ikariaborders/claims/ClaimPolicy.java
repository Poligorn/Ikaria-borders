package com.poligorn.ikariaborders.claims;

import com.poligorn.ikariaborders.api.ClaimActionType;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;

import java.util.UUID;

/** Pure claim decisions (TZ §7). No OPAC or Minecraft types, so it is unit-testable. */
public final class ClaimPolicy {
    /**
     * OPAC's {@code SpecialClaimOwners.SERVER} (= {@code PlayerConfig.SERVER_CLAIM_UUID} = {@code new UUID(0,0)} in
     * 0.32.7, from bytecode). When OPAC is present the integration checks the real constant at startup and warns on mismatch.
     */
    public static final UUID SERVER_OWNER = new UUID(0L, 0L);

    private ClaimPolicy() {}

    public record Verdict(boolean allowed, Zone zone) {}

    /** Only server claims are exempt (and only if {@code global.exempt.serverClaims} is true). No operator exemptions. */
    public static boolean isExempt(ZoneConfig cfg, UUID owner) {
        return owner != null && owner.equals(SERVER_OWNER) && cfg.settings().exemptServerClaims();
    }

    /** Decision for one action at the chunk centre zone. Un-actions are always allowed. */
    public static Verdict check(ZoneConfig cfg, String dimension, int cx, int cz, ClaimActionType action, UUID owner) {
        Zone zone = cfg.zoneOfChunk(dimension, cx, cz);
        if (!action.restrictable() || isExempt(cfg, owner)) return new Verdict(true, zone);
        boolean ok = switch (action) {
            case CLAIM -> zone.claimsAllowed();
            case FORCELOAD -> zone.forceloadAllowed();
            case ANCHOR -> zone.anchorAllowed();
            default -> true;
        };
        return new Verdict(ok, zone);
    }

    /** {@code claims.allow} for a chunk, honouring the server exemption. Backs {@code IkariaBordersAPI.isClaimAllowed}. */
    public static boolean isClaimAllowed(ZoneConfig cfg, String dimension, int cx, int cz, UUID owner) {
        return check(cfg, dimension, cx, cz, ClaimActionType.CLAIM, owner).allowed();
    }

    /** Placeholders: {zone} name, {id}, {dim}, {cx}, {cz}, {action}. */
    public static String formatMessage(String template, Zone zone, String dimension, int cx, int cz, ClaimActionType action) {
        return template.replace("{zone}", zone.name()).replace("{id}", zone.id()).replace("{dim}", dimension)
                .replace("{cx}", Integer.toString(cx)).replace("{cz}", Integer.toString(cz))
                .replace("{action}", action.name().toLowerCase(java.util.Locale.ROOT));
    }
}
