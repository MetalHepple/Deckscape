package uk.darkbyte.deckscape;

/** Pure limits and timing for the bounded static-wallpaper crossfade. */
final class WallpaperTransitionPolicy {
    static final long DURATION_MILLIS = 900L;
    static final long FRAME_DELAY_MILLIS = 50L;
    private static final long MAX_SURFACE_PIXELS = 4_200_000L;

    private WallpaperTransitionPolicy() {}

    static boolean shouldAnimate(boolean enabled, boolean preview, boolean visible,
                                 boolean fromStatic, boolean toStatic,
                                 int width, int height) {
        long pixels = (long) width * height;
        return enabled && !preview && visible && fromStatic && toStatic
                && width > 0 && height > 0 && pixels <= MAX_SURFACE_PIXELS;
    }

    /** Alpha of the outgoing frame, eased from opaque to transparent. */
    static int outgoingAlpha(long elapsedMillis) {
        if (elapsedMillis <= 0) return 255;
        if (elapsedMillis >= DURATION_MILLIS) return 0;
        double progress = elapsedMillis / (double) DURATION_MILLIS;
        double smooth = progress * progress * (3 - 2 * progress);
        return (int) Math.round(255 * (1 - smooth));
    }
}
