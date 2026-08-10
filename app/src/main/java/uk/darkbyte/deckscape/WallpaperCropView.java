package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Interactive preview that shares the production renderer's transform calculations. */
final class WallpaperCropView extends View {
    private static final long FRAME_DELAY_MS = 100L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap bitmap;
    private Movie movie;
    private long movieStartedAt;
    private ScaleMode scaleMode = ScaleMode.DEFAULT;
    private ScaleMode defaultScaleMode = ScaleMode.FILL;
    private float zoom = 1f;
    private float focusX = 0.5f;
    private float focusY = 0.5f;
    private float lastX;
    private float lastY;

    WallpaperCropView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.BLACK);
    }

    void setDecoded(WallpaperPreviewDecoder.Decoded decoded) {
        release();
        if (decoded != null) {
            bitmap = decoded.bitmap;
            movie = decoded.movie;
            movieStartedAt = SystemClock.uptimeMillis();
        }
        invalidate();
    }

    void setProfile(WallpaperProfile profile, ScaleMode globalDefault) {
        WallpaperProfile value = profile == null ? WallpaperProfile.DEFAULT : profile;
        scaleMode = value.scaleMode;
        zoom = value.zoom;
        focusX = value.focusX;
        focusY = value.focusY;
        defaultScaleMode = globalDefault == null ? ScaleMode.FILL : globalDefault;
        invalidate();
    }

    void setScaleMode(ScaleMode value) {
        scaleMode = value == null ? ScaleMode.DEFAULT : value;
        invalidate();
    }

    void setZoom(float value) {
        zoom = Math.max(WallpaperProfile.MIN_ZOOM,
                Math.min(WallpaperProfile.MAX_ZOOM, value));
        invalidate();
    }

    float focusX() {
        return focusX;
    }

    float focusY() {
        return focusY;
    }

    void resetCrop() {
        zoom = 1f;
        focusX = 0.5f;
        focusY = 0.5f;
        invalidate();
    }

    void release() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
        movie = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        int sourceWidth = bitmap != null ? bitmap.getWidth() : movie != null ? movie.width() : 0;
        int sourceHeight = bitmap != null ? bitmap.getHeight() : movie != null ? movie.height() : 0;
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        WallpaperTransform.Result transform = WallpaperTransform.calculate(
                sourceWidth, sourceHeight, getWidth(), getHeight(), scaleMode,
                defaultScaleMode, zoom, focusX, focusY);
        canvas.save();
        canvas.translate(transform.left, transform.top);
        canvas.scale(transform.scaleX, transform.scaleY);
        if (movie != null) {
            int duration = movie.duration() > 0 ? movie.duration() : 1_000;
            movie.setTime((int) ((SystemClock.uptimeMillis() - movieStartedAt) % duration));
            movie.draw(canvas, 0, 0, paint);
        } else {
            canvas.drawBitmap(bitmap, 0, 0, paint);
        }
        canvas.restore();
        if (movie != null && isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(FRAME_DELAY_MS);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleMode != ScaleMode.CUSTOM || sourceWidth() <= 0 || sourceHeight() <= 0) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - lastX;
            float dy = event.getY() - lastY;
            WallpaperTransform.Result transform = WallpaperTransform.calculate(
                    sourceWidth(), sourceHeight(), getWidth(), getHeight(), ScaleMode.CUSTOM,
                    defaultScaleMode, zoom, focusX, focusY);
            float overflowX = Math.max(0, sourceWidth() * transform.scaleX - getWidth());
            float overflowY = Math.max(0, sourceHeight() * transform.scaleY - getHeight());
            if (overflowX > 0) focusX = clamp01(focusX - dx / overflowX);
            if (overflowY > 0) focusY = clamp01(focusY - dy / overflowY);
            lastX = event.getX();
            lastY = event.getY();
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return event.getActionMasked() == MotionEvent.ACTION_CANCEL;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int sourceWidth() {
        return bitmap != null ? bitmap.getWidth() : movie != null ? movie.width() : 0;
    }

    private int sourceHeight() {
        return bitmap != null ? bitmap.getHeight() : movie != null ? movie.height() : 0;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
