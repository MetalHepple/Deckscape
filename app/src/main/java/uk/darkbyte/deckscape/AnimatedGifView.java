package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** Draws a decoded GIF at a bounded 10 fps while the preview remains visible. */
final class AnimatedGifView extends View {
    private static final long FRAME_DELAY_MS = 100L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Movie movie;
    private long startedAt;

    AnimatedGifView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    /** Starts the supplied GIF from its first frame. */
    void setMovie(Movie value) {
        movie = value;
        startedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && movie != null) invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(7, 17, 27));
        if (movie == null || movie.width() <= 0 || movie.height() <= 0) return;

        int duration = movie.duration() > 0 ? movie.duration() : 1_000;
        movie.setTime((int) ((SystemClock.uptimeMillis() - startedAt) % duration));
        float scale = Math.min(getWidth() / (float) movie.width(),
                getHeight() / (float) movie.height());
        float left = (getWidth() - movie.width() * scale) / 2f;
        float top = (getHeight() - movie.height() * scale) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        movie.draw(canvas, 0, 0, paint);
        canvas.restore();

        if (isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(FRAME_DELAY_MS);
        }
    }
}
