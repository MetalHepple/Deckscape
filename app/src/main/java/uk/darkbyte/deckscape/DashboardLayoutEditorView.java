package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Full-dashboard preview with independently draggable passive wallpaper cards. */
final class DashboardLayoutEditorView extends View {
    static final int DASHBOARD_WIDTH = 1_920;
    static final int DASHBOARD_HEIGHT = 1_080;
    private static final long GIF_FRAME_DELAY_MILLIS = 100L;

    private final Paint wallpaperPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dashboardBounds = new RectF(0, 0, DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
    private final WallpaperOverlayRenderer overlayRenderer;
    private final EnumMap<OverlayWidget, OverlayPlacement> placements =
            new EnumMap<>(OverlayWidget.class);
    private final EnumSet<OverlayWidget> enabledWidgets =
            EnumSet.noneOf(OverlayWidget.class);
    private final EnumSet<OverlayWidget> availableWidgets;
    private Bitmap bitmap;
    private Bitmap dashboardReference;
    private Movie movie;
    private long movieStartedAt;
    private WallpaperProfile profile = WallpaperProfile.DEFAULT;
    private ScaleMode defaultScaleMode = ScaleMode.FILL;
    private WeatherSnapshot weather;
    private VehicleTelemetrySnapshot vehicle;
    private boolean snappingEnabled = true;
    private boolean snappedX;
    private boolean snappedY;
    private float snappedGuideX;
    private float snappedGuideY;
    private OverlayWidget selected = OverlayWidget.CLOCK;
    private OverlayWidget dragging;
    private float viewportLeft;
    private float viewportTop;
    private float viewportScale = 1f;

    DashboardLayoutEditorView(Context context) {
        this(context, null);
    }

    DashboardLayoutEditorView(Context context,
                              Map<OverlayWidget, OverlayPlacement> initialPlacements) {
        super(context);
        overlayRenderer = new WallpaperOverlayRenderer(context);
        availableWidgets = OverlayWidget.availableWhen(OverdriveBrand.isInstalled(context));
        for (OverlayWidget widget : OverlayWidget.values()) {
            OverlayPlacement initial = initialPlacements == null
                    ? null : initialPlacements.get(widget);
            placements.put(widget, initial == null ? widget.defaultPlacement : initial);
        }
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Ui.BACKGROUND);
        setContentDescription("Dashboard widget layout preview");
    }

    void setDecoded(WallpaperPreviewDecoder.Decoded decoded) {
        releaseSelectedWallpaper();
        if (decoded != null) {
            bitmap = decoded.bitmap;
            movie = decoded.movie;
            movieStartedAt = SystemClock.uptimeMillis();
        }
        invalidate();
    }

    void setDashboardReference(Bitmap reference) {
        if (dashboardReference != null && !dashboardReference.isRecycled()) {
            dashboardReference.recycle();
        }
        dashboardReference = reference;
        invalidate();
    }

    void setWallpaperProfile(WallpaperProfile value, ScaleMode globalDefault) {
        profile = value == null ? WallpaperProfile.DEFAULT : value;
        defaultScaleMode = globalDefault == null ? ScaleMode.FILL : globalDefault;
        invalidate();
    }

    void setWidgetState(EnumSet<OverlayWidget> enabled, WeatherSnapshot weatherSnapshot,
                        VehicleTelemetrySnapshot vehicleSnapshot) {
        enabledWidgets.clear();
        if (enabled != null) enabledWidgets.addAll(enabled);
        enabledWidgets.retainAll(availableWidgets);
        if (selected == null || !enabledWidgets.contains(selected)) {
            selected = enabledWidgets.isEmpty() ? null : enabledWidgets.iterator().next();
        }
        weather = weatherSnapshot;
        vehicle = vehicleSnapshot;
        invalidate();
    }

    void selectWidget(OverlayWidget widget) {
        if (widget != null && enabledWidgets.contains(widget)) {
            selected = widget;
            invalidate();
        }
    }

    EnumMap<OverlayWidget, OverlayPlacement> placements() {
        return new EnumMap<>(placements);
    }

    boolean snappingEnabled() {
        return snappingEnabled;
    }

    void setSnappingEnabled(boolean enabled) {
        snappingEnabled = enabled;
        snappedX = false;
        snappedY = false;
        invalidate();
    }

    void resetPlacements() {
        for (OverlayWidget widget : OverlayWidget.values()) {
            placements.put(widget, widget.defaultPlacement);
        }
        selected = enabledWidgets.isEmpty() ? null : enabledWidgets.iterator().next();
        invalidate();
    }

    void release() {
        releaseSelectedWallpaper();
        if (dashboardReference != null && !dashboardReference.isRecycled()) {
            dashboardReference.recycle();
        }
        dashboardReference = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Ui.BACKGROUND);
        viewportScale = Math.min(getWidth() / (float) DASHBOARD_WIDTH,
                getHeight() / (float) DASHBOARD_HEIGHT);
        if (viewportScale <= 0) return;
        float viewportWidth = DASHBOARD_WIDTH * viewportScale;
        float viewportHeight = DASHBOARD_HEIGHT * viewportScale;
        viewportLeft = (getWidth() - viewportWidth) / 2f;
        viewportTop = (getHeight() - viewportHeight) / 2f;

        canvas.save();
        canvas.translate(viewportLeft, viewportTop);
        canvas.scale(viewportScale, viewportScale);
        canvas.clipRect(0, 0, DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
        if (dashboardReference != null && !dashboardReference.isRecycled()) {
            canvas.drawBitmap(dashboardReference, null, dashboardBounds, wallpaperPaint);
        } else {
            drawWallpaper(canvas);
        }
        overlayRenderer.draw(canvas, DASHBOARD_WIDTH, DASHBOARD_HEIGHT,
                enabledWidgets, placements, weather, vehicle,
                System.currentTimeMillis());
        drawSnapGuides(canvas);
        drawSelection(canvas);
        canvas.restore();

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(Math.max(1, viewportScale * 2));
        guidePaint.setColor(Ui.DIVIDER);
        canvas.drawRect(viewportLeft, viewportTop, viewportLeft + viewportWidth,
                viewportTop + viewportHeight, guidePaint);

        if (isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(movie == null ? 1_000L : GIF_FRAME_DELAY_MILLIS);
        }
    }

    private void drawWallpaper(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        int sourceWidth = bitmap != null ? bitmap.getWidth() : movie != null ? movie.width() : 0;
        int sourceHeight = bitmap != null ? bitmap.getHeight() : movie != null ? movie.height() : 0;
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            guidePaint.setStyle(Paint.Style.FILL);
            guidePaint.setTextAlign(Paint.Align.CENTER);
            guidePaint.setTextSize(28);
            guidePaint.setColor(Ui.MUTED);
            canvas.drawText("Current wallpaper preview unavailable", DASHBOARD_WIDTH / 2f,
                    DASHBOARD_HEIGHT / 2f, guidePaint);
            return;
        }
        WallpaperTransform.Result transform = WallpaperTransform.calculate(
                sourceWidth, sourceHeight, DASHBOARD_WIDTH, DASHBOARD_HEIGHT,
                profile.scaleMode, defaultScaleMode, profile.zoom, profile.focusX,
                profile.focusY);
        canvas.save();
        canvas.translate(transform.left, transform.top);
        canvas.scale(transform.scaleX, transform.scaleY);
        if (movie != null) {
            int duration = movie.duration() > 0 ? movie.duration() : 1_000;
            movie.setTime((int) ((SystemClock.uptimeMillis() - movieStartedAt) % duration));
            movie.draw(canvas, 0, 0, wallpaperPaint);
        } else {
            canvas.drawBitmap(bitmap, 0, 0, wallpaperPaint);
        }
        canvas.restore();
    }

    private void drawSelection(Canvas canvas) {
        if (selected == null) return;
        RectF bounds = boundsFor(selected);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(4);
        guidePaint.setColor(Ui.CYAN);
        RectF highlight = new RectF(bounds);
        highlight.inset(-8, -8);
        canvas.drawRoundRect(highlight, 22, 22, guidePaint);

        String label = "DRAG " + selected.editorLabel;
        guidePaint.setStyle(Paint.Style.FILL);
        guidePaint.setTextAlign(Paint.Align.CENTER);
        guidePaint.setTextSize(17);
        float labelWidth = guidePaint.measureText(label) + 28;
        float labelTop = Math.max(5, highlight.top - 31);
        RectF badge = new RectF(highlight.centerX() - labelWidth / 2f, labelTop,
                highlight.centerX() + labelWidth / 2f, labelTop + 27);
        guidePaint.setColor(Ui.CYAN);
        canvas.drawRoundRect(badge, 10, 10, guidePaint);
        guidePaint.setColor(Ui.NAV);
        canvas.drawText(label, badge.centerX(), badge.top + 19, guidePaint);
    }

    private void drawSnapGuides(Canvas canvas) {
        if (dragging == null || (!snappedX && !snappedY)) return;
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(3);
        guidePaint.setColor(0xdd42d9e8);
        if (snappedX) {
            canvas.drawLine(snappedGuideX, 0, snappedGuideX, DASHBOARD_HEIGHT, guidePaint);
        }
        if (snappedY) {
            canvas.drawLine(0, snappedGuideY, DASHBOARD_WIDTH, snappedGuideY, guidePaint);
        }
    }

    private RectF boundsFor(OverlayWidget widget) {
        return overlayRenderer.boundsFor(widget, DASHBOARD_WIDTH, DASHBOARD_HEIGHT,
                placements.get(widget));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float logicalX = (event.getX() - viewportLeft) / viewportScale;
        float logicalY = (event.getY() - viewportTop) / viewportScale;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = targetAt(logicalX, logicalY);
                if (dragging == null) return false;
                selected = dragging;
                getParent().requestDisallowInterceptTouchEvent(true);
                updateDraggedPlacement(logicalX, logicalY);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging == null) return false;
                updateDraggedPlacement(logicalX, logicalY);
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging == null) return false;
                updateDraggedPlacement(logicalX, logicalY);
                dragging = null;
                snappedX = false;
                snappedY = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = null;
                snappedX = false;
                snappedY = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private OverlayWidget targetAt(float x, float y) {
        OverlayWidget nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        for (OverlayWidget widget : enabledWidgets) {
            RectF bounds = boundsFor(widget);
            if (!expanded(bounds, 22).contains(x, y)) continue;
            float distance = distanceSquared(x, y, bounds);
            if (distance < nearestDistance) {
                nearest = widget;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void updateDraggedPlacement(float x, float y) {
        float resolvedX = x;
        float resolvedY = y;
        snappedX = false;
        snappedY = false;
        if (snappingEnabled && dragging != null) {
            float[] xGuides = new float[enabledWidgets.size() - 1];
            float[] yGuides = new float[enabledWidgets.size() - 1];
            int index = 0;
            for (OverlayWidget widget : enabledWidgets) {
                if (widget == dragging) continue;
                RectF otherBounds = boundsFor(widget);
                xGuides[index] = otherBounds.centerX();
                yGuides[index] = otherBounds.centerY();
                index++;
            }
            OverlaySnapper.Result result = OverlaySnapper.snapToNearest(x, y,
                    xGuides, yGuides, OverlaySnapper.THRESHOLD_PX);
            resolvedX = result.x;
            resolvedY = result.y;
            snappedX = result.snappedX;
            snappedY = result.snappedY;
            snappedGuideX = result.guideX;
            snappedGuideY = result.guideY;
        }
        placements.put(dragging, new OverlayPlacement(resolvedX / DASHBOARD_WIDTH,
                resolvedY / DASHBOARD_HEIGHT));
        invalidate();
    }

    private static RectF expanded(RectF value, float amount) {
        RectF result = new RectF(value);
        result.inset(-amount, -amount);
        return result;
    }

    private static float distanceSquared(float x, float y, RectF bounds) {
        float deltaX = x - bounds.centerX();
        float deltaY = y - bounds.centerY();
        return deltaX * deltaX + deltaY * deltaY;
    }

    private void releaseSelectedWallpaper() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
        movie = null;
    }
}
