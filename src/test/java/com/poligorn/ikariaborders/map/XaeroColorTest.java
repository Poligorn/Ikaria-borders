package com.poligorn.ikariaborders.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class XaeroColorTest {
    @Test
    void spawnGreenIsBgraNotArgb() {
        int c = XaeroColor.pack(0x55FF55, 0.25);
        assertEquals(0x55FF5540, c);
        assertNotEquals(0x4055FF55, c); // the ARGB reading that rendered as salmon
    }

    @Test
    void channelOrder() {
        assertEquals(0x0000FFFF, XaeroColor.pack(0xFF0000, 1.0));
        assertEquals(0xFF0000FF, XaeroColor.pack(0x0000FF, 1.0));
    }

    @Test
    void zeroAlphaIsZeroAndValuesClamp() {
        assertEquals(0, XaeroColor.pack(0x55FF55, 0.0));
        assertEquals(0, XaeroColor.pack(0x55FF55, 0));
        assertEquals(0, XaeroColor.pack(0x55FF55, -3));
        assertEquals(0, XaeroColor.pack(0x55FF55, Double.NaN));
        assertEquals(0x55FF55FF, XaeroColor.pack(0x55FF55, 7.0));
        assertEquals(0x55FF55FF, XaeroColor.pack(0x55FF55, 999));
        assertEquals(0x55FF55FF, XaeroColor.pack(0xFF55FF55, 255)); // upper byte ignored
    }
}
