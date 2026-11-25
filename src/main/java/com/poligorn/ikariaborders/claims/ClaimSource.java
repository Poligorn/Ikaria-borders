package com.poligorn.ikariaborders.claims;

import java.util.List;
import java.util.UUID;

/** Read side of the claim store (OPAC adapter in production, fake in tests). Main thread only. */
public interface ClaimSource {
    /** Owners that have claim data. */
    List<UUID> owners();

    String ownerName(UUID owner);

    /** Snapshot of one owner's claims and anchors; safe to modify the store while iterating it. */
    List<ClaimEntry> claimsOf(UUID owner);
}
