package com.poligorn.ikariaborders.claims;

import java.util.UUID;

/** Write side of the claim store. Each method returns true if something was actually changed. */
public interface ClaimRemover {
    boolean unclaim(UUID owner, ClaimEntry e);

    boolean unforceload(UUID owner, ClaimEntry e);

    boolean removeAnchor(UUID owner, ClaimEntry e);
}
