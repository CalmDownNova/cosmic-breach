package com.cosmicbreach.client.dev;

import java.util.Locale;
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;

/** Spots a screenshot that is one flat colour (black, white, clear colour): a capture that went wrong. */
final class BlankFrameCheck {
    private static final int COLUMNS = 64;
    private static final int ROWS = 36;
    private static final int MIN_SPREAD = 7;

    private BlankFrameCheck() {}

    /**
     * Samples a 64 by 36 grid. Returns why the frame looks blank, or null if it has content.
     *
     * @param abgrAt pixel at (x, y) packed as NativeImage stores it (red in the low byte)
     */
    @Nullable
    static String blankReason(int width, int height, IntBinaryOperator abgrAt) {
        int[] min = {255, 255, 255};
        int[] max = {0, 0, 0};
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int x = (int) ((column + 0.5) * width / COLUMNS);
                int y = (int) ((row + 0.5) * height / ROWS);
                int abgr = abgrAt.applyAsInt(x, y);
                for (int channel = 0; channel < 3; channel++) {
                    int value = (abgr >> (8 * channel)) & 0xFF;
                    min[channel] = Math.min(min[channel], value);
                    max[channel] = Math.max(max[channel], value);
                }
            }
        }
        int spread = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        if (spread >= MIN_SPREAD) {
            return null;
        }
        return String.format(Locale.ROOT, "every sampled pixel is close to #%02x%02x%02x", min[0], min[1], min[2]);
    }
}
