package uk.darkbyte.deckscape;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Lightweight, dependency-free folder glyph used by category and source cards. */
final class FolderDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    FolderDrawable(int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float unit = Math.min(bounds.width(), bounds.height()) / 28f;
        float left = bounds.left + 2f * unit;
        float top = bounds.top + 7f * unit;
        float right = bounds.right - 2f * unit;
        float bottom = bounds.bottom - 3f * unit;
        float radius = 3f * unit;
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        canvas.drawRoundRect(new RectF(left + 2f * unit, bounds.top + 4f * unit,
                left + 13f * unit, top + 5f * unit), radius, radius, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
