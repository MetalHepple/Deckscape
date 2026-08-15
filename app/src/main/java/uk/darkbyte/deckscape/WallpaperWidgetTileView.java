package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.EnumMap;
import java.util.EnumSet;

/** One live widget example for the scrollable catalogue beside the layout canvas. */
final class WallpaperWidgetTileView extends View {
    private static final int LOGICAL_WIDTH = 660;
    private static final int LOGICAL_HEIGHT = 220;

    private final OverlayWidget widget;
    private final WallpaperOverlayRenderer renderer;
    private final EnumMap<OverlayWidget, OverlayPlacement> placement =
            new EnumMap<>(OverlayWidget.class);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF badgeBounds = new RectF();
    private final Runnable clockTick = this::invalidate;
    private boolean enabled;
    private WeatherSnapshot weather;
    private VehicleTelemetrySnapshot vehicle;

    WallpaperWidgetTileView(Context context) {
        this(context, OverlayWidget.CLOCK);
    }

    WallpaperWidgetTileView(Context context, OverlayWidget widget) {
        super(context);
        this.widget = widget;
        renderer = new WallpaperOverlayRenderer(context);
        placement.put(widget, new OverlayPlacement(0.5f, 0.5f));
        badgePaint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        setClickable(true);
        setFocusable(true);
        setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(context, 10),
                Ui.DIVIDER, Ui.dp(context, 1)));
        updateContentDescription();
    }

    void setState(boolean isEnabled, WeatherSnapshot weatherSnapshot,
                  VehicleTelemetrySnapshot vehicleSnapshot) {
        enabled = isEnabled;
        weather = weatherSnapshot;
        vehicle = vehicleSnapshot;
        updateContentDescription();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / (float) LOGICAL_WIDTH,
                getHeight() / (float) LOGICAL_HEIGHT);
        if (scale <= 0) return;
        float left = (getWidth() - LOGICAL_WIDTH * scale) / 2f;
        float top = (getHeight() - LOGICAL_HEIGHT * scale) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        renderer.draw(canvas, LOGICAL_WIDTH, LOGICAL_HEIGHT, EnumSet.of(widget),
                placement, weather, vehicle, System.currentTimeMillis());
        drawStateBadge(canvas);
        canvas.restore();
        if (widget == OverlayWidget.CLOCK && isShown()) {
            removeCallbacks(clockTick);
            postDelayed(clockTick, WallpaperRedrawScheduler.staticDelayMillis(
                    System.currentTimeMillis(), true));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(clockTick);
        super.onDetachedFromWindow();
    }

    private void drawStateBadge(Canvas canvas) {
        RectF card = renderer.boundsFor(widget, LOGICAL_WIDTH, LOGICAL_HEIGHT,
                placement.get(widget));
        String label = enabled ? "ON" : "OFF";
        badgePaint.setTextAlign(Paint.Align.CENTER);
        badgePaint.setTextSize(17);
        float width = badgePaint.measureText(label) + 26;
        badgeBounds.set(card.right - width - 10, card.top + 9,
                card.right - 10, card.top + 38);
        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(enabled ? Ui.CYAN : Ui.CORAL);
        canvas.drawRoundRect(badgeBounds, 9, 9, badgePaint);
        badgePaint.setColor(Color.rgb(7, 17, 27));
        canvas.drawText(label, badgeBounds.centerX(), badgeBounds.top + 21, badgePaint);
    }

    private void updateContentDescription() {
        setContentDescription(widget.editorLabel + " wallpaper widget, "
                + (enabled ? "on" : "off") + ". Tap to toggle.");
    }
}
