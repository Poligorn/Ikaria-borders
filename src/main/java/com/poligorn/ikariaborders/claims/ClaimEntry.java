package com.poligorn.ikariaborders.claims;

/** One claimed chunk (or one anchor) of an owner, as a snapshot independent of OPAC types. */
public record ClaimEntry(Kind kind, String dimension, int cx, int cz, boolean forceloaded, int subConfig) {
    public enum Kind { CLAIM, ANCHOR }

    public static ClaimEntry claim(String dim, int cx, int cz, boolean forceloaded) {
        return new ClaimEntry(Kind.CLAIM, dim, cx, cz, forceloaded, -1);
    }

    public static ClaimEntry anchor(String dim, int cx, int cz) {
        return new ClaimEntry(Kind.ANCHOR, dim, cx, cz, false, -1);
    }
}
