package uk.darkbyte.deckscape;

/** Normalized centre point for one independently positioned wallpaper card. */
final class OverlayPlacement {
    static final OverlayPlacement DEFAULT_CLOCK = new OverlayPlacement(0.16f, 0.36f);
    static final OverlayPlacement DEFAULT_WEATHER = new OverlayPlacement(0.38f, 0.36f);

    final float x;
    final float y;

    OverlayPlacement(float x, float y) {
        this.x = clamp(x);
        this.y = clamp(y);
    }

    static OverlayPlacement legacyClock(OverlayPosition position) {
        OverlayPosition value = position == null ? OverlayPosition.TOP_LEFT : position;
        float x = value == OverlayPosition.TOP_RIGHT || value == OverlayPosition.BOTTOM_RIGHT
                ? 0.679f : 0.101f;
        return new OverlayPlacement(x, isBottom(value) ? 0.903f : 0.097f);
    }

    static OverlayPlacement legacyWeather(OverlayPosition position) {
        OverlayPosition value = position == null ? OverlayPosition.TOP_LEFT : position;
        float x = value == OverlayPosition.TOP_RIGHT || value == OverlayPosition.BOTTOM_RIGHT
                ? 0.876f : 0.298f;
        return new OverlayPlacement(x, isBottom(value) ? 0.903f : 0.097f);
    }

    private static boolean isBottom(OverlayPosition position) {
        return position == OverlayPosition.BOTTOM_LEFT
                || position == OverlayPosition.BOTTOM_RIGHT;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.5f;
        return Math.max(0f, Math.min(1f, value));
    }
}
