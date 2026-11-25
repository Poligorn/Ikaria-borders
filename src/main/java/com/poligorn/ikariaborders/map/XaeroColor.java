package com.poligorn.ikariaborders.map;

/**
 * Xaero stores highlight colours as BGRA {@code 0xBBGGRRAA} (alpha in the LOW byte), not ARGB:
 * see {@code AbstractHighlighter.getBlend} and OPAC's {@code ClaimsHighlighter}. Pure JVM.
 */
public final class XaeroColor {
    private XaeroColor() {}

    /** @param rgb 0xRRGGBB (upper byte ignored), @param alpha 0..255 (clamped); alpha 0 gives 0. */
    public static int pack(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        if (a == 0) return 0;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (b << 24) | (g << 16) | (r << 8) | a;
    }

    /** @param opacity 0..1 (clamped, NaN = 0). */
    public static int pack(int rgb, double opacity) {
        if (Double.isNaN(opacity)) return 0;
        return pack(rgb, (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255));
    }
}
