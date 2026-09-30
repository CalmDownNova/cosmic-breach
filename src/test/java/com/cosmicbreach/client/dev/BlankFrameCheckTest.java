package com.cosmicbreach.client.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BlankFrameCheckTest {
    private static final int OPAQUE = 0xFF000000;

    @Test
    void aBlackFrameIsBlank() {
        assertEquals("every sampled pixel is close to #000000",
                BlankFrameCheck.blankReason(1280, 720, (x, y) -> OPAQUE));
    }

    @Test
    void aFlatColourWithSlightNoiseIsStillBlank() {
        // Clear colour #7fa5ff (stored ABGR) with +-2 of dithering noise.
        assertNotNull(BlankFrameCheck.blankReason(1280, 720, (x, y) -> OPAQUE | 0xFFA57F + ((x + y) % 3)));
    }

    @Test
    void skyOverGrassHasContent() {
        int sky = OPAQUE | 0xFFB080;   // #80b0ff
        int grass = OPAQUE | 0x2E5B4A; // #4a5b2e
        assertNull(BlankFrameCheck.blankReason(1280, 720, (x, y) -> y < 370 ? sky : grass));
    }
}
