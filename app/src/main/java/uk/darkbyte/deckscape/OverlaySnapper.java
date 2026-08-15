package uk.darkbyte.deckscape;

/** Pure centre-line snapping used by the dashboard drag editor. */
final class OverlaySnapper {
    static final float THRESHOLD_PX = 40f;

    static final class Result {
        final float x;
        final float y;
        final boolean snappedX;
        final boolean snappedY;
        final float guideX;
        final float guideY;

        Result(float x, float y, boolean snappedX, boolean snappedY) {
            this(x, y, snappedX, snappedY, snappedX ? x : Float.NaN,
                    snappedY ? y : Float.NaN);
        }

        Result(float x, float y, boolean snappedX, boolean snappedY,
               float guideX, float guideY) {
            this.x = x;
            this.y = y;
            this.snappedX = snappedX;
            this.snappedY = snappedY;
            this.guideX = guideX;
            this.guideY = guideY;
        }
    }

    private OverlaySnapper() {}

    static Result snap(float x, float y, float otherX, float otherY, float threshold) {
        boolean snappedX = Math.abs(x - otherX) <= threshold;
        boolean snappedY = Math.abs(y - otherY) <= threshold;
        return new Result(snappedX ? otherX : x, snappedY ? otherY : y,
                snappedX, snappedY);
    }

    static Result snapToNearest(float x, float y, float[] otherXs, float[] otherYs,
                                float threshold) {
        float nearestX = Float.NaN;
        float nearestY = Float.NaN;
        float nearestDeltaX = Float.POSITIVE_INFINITY;
        float nearestDeltaY = Float.POSITIVE_INFINITY;
        if (otherXs != null) {
            for (float candidate : otherXs) {
                float delta = Math.abs(x - candidate);
                if (delta <= threshold && delta < nearestDeltaX) {
                    nearestDeltaX = delta;
                    nearestX = candidate;
                }
            }
        }
        if (otherYs != null) {
            for (float candidate : otherYs) {
                float delta = Math.abs(y - candidate);
                if (delta <= threshold && delta < nearestDeltaY) {
                    nearestDeltaY = delta;
                    nearestY = candidate;
                }
            }
        }
        boolean snappedX = !Float.isNaN(nearestX);
        boolean snappedY = !Float.isNaN(nearestY);
        return new Result(snappedX ? nearestX : x, snappedY ? nearestY : y,
                snappedX, snappedY, nearestX, nearestY);
    }
}
