package com.poligorn.ikariaborders.api;

/** Mirror of OPAC's {@code ClaimingAction} so API users and tests need no OPAC classes. */
public enum ClaimActionType {
    CLAIM, UNCLAIM, FORCELOAD, UNFORCELOAD, UNCLAIM_UNANCHORED, ANCHOR, UNANCHOR;

    /** CLAIM, FORCELOAD and ANCHOR can be forbidden by zone rules; the rest are always allowed. */
    public boolean restrictable() {
        return this == CLAIM || this == FORCELOAD || this == ANCHOR;
    }
}
