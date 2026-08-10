package uk.darkbyte.deckscape;

/** Calculates deterministic image-to-canvas transforms for every supported display mode. */
final class WallpaperTransform {
    private WallpaperTransform() {}

    static Result calculate(int sourceWidth, int sourceHeight, int canvasWidth, int canvasHeight,
                            ScaleMode requestedMode, ScaleMode defaultMode, float zoom,
                            float focusX, float focusY) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || canvasWidth <= 0 || canvasHeight <= 0) {
            return new Result(1f, 1f, 0f, 0f);
        }
        ScaleMode mode = requestedMode == null || requestedMode == ScaleMode.DEFAULT
                ? normalizedDefault(defaultMode) : requestedMode;
        float widthScale = canvasWidth / (float) sourceWidth;
        float heightScale = canvasHeight / (float) sourceHeight;
        if (mode == ScaleMode.STRETCH) {
            return new Result(widthScale, heightScale, 0f, 0f);
        }

        float scale = mode == ScaleMode.FIT
                ? Math.min(widthScale, heightScale) : Math.max(widthScale, heightScale);
        float boundedZoom = mode == ScaleMode.CUSTOM
                ? Math.max(WallpaperProfile.MIN_ZOOM,
                        Math.min(WallpaperProfile.MAX_ZOOM, zoom)) : 1f;
        scale *= boundedZoom;
        float scaledWidth = sourceWidth * scale;
        float scaledHeight = sourceHeight * scale;
        float left;
        float top;
        if (mode == ScaleMode.CUSTOM) {
            float boundedX = Math.max(0f, Math.min(1f, focusX));
            float boundedY = Math.max(0f, Math.min(1f, focusY));
            left = scaledWidth <= canvasWidth ? (canvasWidth - scaledWidth) / 2f
                    : -(scaledWidth - canvasWidth) * boundedX;
            top = scaledHeight <= canvasHeight ? (canvasHeight - scaledHeight) / 2f
                    : -(scaledHeight - canvasHeight) * boundedY;
        } else {
            left = (canvasWidth - scaledWidth) / 2f;
            top = (canvasHeight - scaledHeight) / 2f;
        }
        return new Result(scale, scale, left, top);
    }

    private static ScaleMode normalizedDefault(ScaleMode value) {
        return value == null || value == ScaleMode.DEFAULT || value == ScaleMode.CUSTOM
                ? ScaleMode.FILL : value;
    }

    /** Immutable scale and translation values applied before drawing source pixels. */
    static final class Result {
        final float scaleX;
        final float scaleY;
        final float left;
        final float top;

        Result(float scaleX, float scaleY, float left, float top) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.left = left;
            this.top = top;
        }
    }
}
