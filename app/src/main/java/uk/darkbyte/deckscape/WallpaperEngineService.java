package uk.darkbyte.deckscape;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.File;
import java.util.List;

/**
 * Android live-wallpaper service that renders the selected image and performs timed rotation.
 * Animated GIF redraws stop whenever the wallpaper is hidden to avoid background CPU use.
 */
public final class WallpaperEngineService extends WallpaperService {
    private static final String TAG = "Deckscape";
    static final String ACTION_NEXT = BuildConfig.APPLICATION_ID + ".NEXT";
    static final String ACTION_LIBRARY_CHANGED = BuildConfig.APPLICATION_ID + ".LIBRARY_CHANGED";
    static final String PREFS = "wallpaper_preferences";
    static final String PREF_INTERVAL = "rotation_interval_ms";
    static final String PREF_CURRENT_FILE = "current_file";
    static final String PREF_LAST_SWITCH = "last_switch_ms";
    static final String PREF_DECODE_STATUS = "last_decode_status";
    static final String PREF_RENDER_STATUS = "last_render_status";
    static final String PREF_MANUAL_OVERRIDE = "manual_wallpaper_override";
    static final String PREF_LAST_PHASE = "last_day_phase";
    static final long DEFAULT_INTERVAL = 3_600_000L;

    @Override
    public Engine onCreateEngine() {
        return new GalleryEngine();
    }

    private final class GalleryEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Runnable drawRunnable = this::drawFrame;
        private final SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        private final DayNightSettings dayNight = new DayNightSettings(WallpaperEngineService.this);
        private final WallpaperProfileStore profiles =
                new WallpaperProfileStore(WallpaperEngineService.this);
        private final AmbientLightTracker lightTracker = new AmbientLightTracker();
        private final SensorManager sensorManager =
                (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        private final Sensor lightSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        private final SensorEventListener lightListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                DayPhase before = lightTracker.phase();
                DayPhase after = lightTracker.update(event.values[0], SystemClock.elapsedRealtime());
                if (after != before) drawSoon();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // Schedule changes use debounced readings; accuracy callbacks need no action.
            }
        };
        private boolean visible;
        private boolean receiverRegistered;
        private boolean sensorRegistered;
        private int surfaceWidth;
        private int surfaceHeight;
        private File loadedFile;
        private Bitmap bitmap;
        private Movie movie;
        private long movieStartUptime;
        private String lastRenderStatus = "";

        private final BroadcastReceiver commands = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_NEXT.equals(intent.getAction())) nextWallpaper();
                else {
                    updateLightSensor();
                    releaseDecoded();
                    drawSoon();
                }
            }
        };

        @SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 has no export-flag overload.
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_NEXT);
            filter.addAction(ACTION_LIBRARY_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                WallpaperEngineService.this.registerReceiver(
                        commands, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                WallpaperEngineService.this.registerReceiver(commands, filter);
            }
            receiverRegistered = true;
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            updateLightSensor();
            if (visible) drawSoon();
            else handler.removeCallbacks(drawRunnable);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surfaceWidth = width;
            surfaceHeight = height;
            releaseDecoded();
            drawSoon();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(drawRunnable);
            releaseDecoded();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunnable);
            unregisterLightSensor();
            releaseDecoded();
            if (receiverRegistered) {
                try {
                    WallpaperEngineService.this.unregisterReceiver(commands);
                } catch (IllegalArgumentException ignored) {
                    // The framework can tear the receiver down first.
                }
            }
            super.onDestroy();
        }

        private void drawSoon() {
            handler.removeCallbacks(drawRunnable);
            if (visible) handler.post(drawRunnable);
        }

        private void drawFrame() {
            if (!visible) return;
            if (dayNight.disableIfIncomplete()) updateLightSensor();
            long now = System.currentTimeMillis();
            DayPhase phase = dayNight.isEnabled()
                    ? dayNight.currentPhase(now, lightTracker.phase()) : null;
            List<File> files = phase == null ? WallpaperStore.list(WallpaperEngineService.this)
                    : dayNight.eligibleFiles(phase);
            if (files.isEmpty()) {
                drawFallback("Choose a wallpaper in Deckscape");
                return;
            }

            long lastSwitch = preferences.getLong(PREF_LAST_SWITCH, 0);
            long interval = preferences.getLong(PREF_INTERVAL, DEFAULT_INTERVAL);
            File selected = WallpaperStore.selectedDownloaded(WallpaperEngineService.this);
            boolean manualOverride = preferences.getBoolean(PREF_MANUAL_OVERRIDE, false)
                    && selected != null;
            String savedPhase = preferences.getString(PREF_LAST_PHASE, "");
            boolean phaseChanged = phase != null && !phase.name().equals(savedPhase);

            if (phaseChanged) {
                selected = WallpaperStore.nextForPhase(
                        WallpaperEngineService.this, selected, phase);
                if (selected != null) WallpaperStore.selectForEngine(
                        WallpaperEngineService.this, selected);
                preferences.edit().putString(PREF_LAST_PHASE, phase.name()).apply();
                lastSwitch = now;
                manualOverride = false;
                releaseDecoded();
            } else if (manualOverride && interval > 0 && lastSwitch > 0
                    && now - lastSwitch >= interval) {
                manualOverride = false;
                preferences.edit().putBoolean(PREF_MANUAL_OVERRIDE, false).apply();
            }

            if (!manualOverride && (selected == null || !files.contains(selected))) {
                selected = WallpaperStore.current(WallpaperEngineService.this, files);
                WallpaperStore.selectForEngine(WallpaperEngineService.this, selected);
                lastSwitch = now;
                releaseDecoded();
            }
            int index = Math.max(0, files.indexOf(selected));
            if (lastSwitch == 0) {
                preferences.edit().putLong(PREF_LAST_SWITCH, now).apply();
            } else if (!manualOverride
                    && RotationPolicy.shouldRotate(now, lastSwitch, interval, files.size())) {
                index = RotationPolicy.nextIndex(index, files.size());
                selected = files.get(index);
                WallpaperStore.selectForEngine(WallpaperEngineService.this, selected);
                releaseDecoded();
            }

            if (!selected.equals(loadedFile)) load(selected);
            drawLoaded(selected.getName());
            handler.removeCallbacks(drawRunnable);
            if (visible) handler.postDelayed(drawRunnable, movie != null ? 100L : 15_000L);
        }

        private void nextWallpaper() {
            DayPhase phase = dayNight.isEnabled()
                    ? dayNight.currentPhase(System.currentTimeMillis(), lightTracker.phase()) : null;
            List<File> files = phase == null ? WallpaperStore.list(WallpaperEngineService.this)
                    : dayNight.eligibleFiles(phase);
            if (files.isEmpty()) return;
            File selected = WallpaperStore.selectedDownloaded(WallpaperEngineService.this);
            int current = files.indexOf(selected);
            int next = current < 0 ? 0 : RotationPolicy.nextIndex(current, files.size());
            WallpaperStore.selectForEngine(WallpaperEngineService.this, files.get(next));
            releaseDecoded();
            drawSoon();
        }

        private void load(File file) {
            releaseDecoded();
            loadedFile = file;
            String status;
            if (WallpaperRules.isGif(file.getName())) {
                movie = Movie.decodeFile(file.getAbsolutePath());
                movieStartUptime = SystemClock.uptimeMillis();
                status = movie == null ? "GIF decode failed: " + file.getName()
                        : "GIF " + movie.width() + "×" + movie.height() + " • "
                        + movie.duration() + " ms";
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                int sample = 1;
                while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                status = bitmap == null ? "Image decode failed: " + file.getName()
                        : "Image " + bitmap.getWidth() + "×" + bitmap.getHeight();
            }
            preferences.edit().putString(PREF_DECODE_STATUS, status).apply();
            Log.i(TAG, status + " • " + file.getName());
        }

        private void drawLoaded(String name) {
            SurfaceHolder holder = getSurfaceHolder();
            Surface surface = holder.getSurface();
            if (surface == null || !surface.isValid()) return;
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);
                int canvasWidth = canvas.getWidth();
                int canvasHeight = canvas.getHeight();
                WallpaperProfile profile = profiles.get(loadedFile);
                if (movie != null && movie.width() > 0 && movie.height() > 0) {
                    int duration = movie.duration() > 0 ? movie.duration() : 1_000;
                    movie.setTime((int) ((SystemClock.uptimeMillis() - movieStartUptime) % duration));
                    WallpaperTransform.Result transform = WallpaperTransform.calculate(
                            movie.width(), movie.height(), canvasWidth, canvasHeight,
                            profile.scaleMode, dayNight.defaultScaleMode(), profile.zoom,
                            profile.focusX, profile.focusY);
                    canvas.save();
                    canvas.translate(transform.left, transform.top);
                    canvas.scale(transform.scaleX, transform.scaleY);
                    movie.draw(canvas, 0, 0, bitmapPaint);
                    canvas.restore();
                    recordRenderStatus(name, movie.width(), movie.height(), canvasWidth,
                            canvasHeight, profile, transform);
                } else if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                    WallpaperTransform.Result transform = WallpaperTransform.calculate(
                            bitmap.getWidth(), bitmap.getHeight(), canvasWidth, canvasHeight,
                            profile.scaleMode, dayNight.defaultScaleMode(), profile.zoom,
                            profile.focusX, profile.focusY);
                    canvas.save();
                    canvas.translate(transform.left, transform.top);
                    canvas.scale(transform.scaleX, transform.scaleY);
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                    canvas.restore();
                    recordRenderStatus(name, bitmap.getWidth(), bitmap.getHeight(), canvasWidth,
                            canvasHeight, profile, transform);
                } else {
                    drawText(canvas, "Unable to decode " + name);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        private void drawFallback(String message) {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.rgb(7, 17, 27));
                drawText(canvas, message);
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        private void drawText(Canvas canvas, String message) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTextSize(38);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(message, canvas.getWidth() / 2f, canvas.getHeight() / 2f, paint);
        }

        private void recordRenderStatus(String name, int sourceWidth, int sourceHeight,
                                        int canvasWidth, int canvasHeight,
                                        WallpaperProfile profile,
                                        WallpaperTransform.Result transform) {
            String status = name + " | source " + sourceWidth + "x" + sourceHeight
                    + " | surface " + surfaceWidth + "x" + surfaceHeight
                    + " | canvas " + canvasWidth + "x" + canvasHeight
                    + " | " + profile.scaleMode.name()
                    + " | scale " + String.format(java.util.Locale.ROOT, "%.3fx%.3f",
                    transform.scaleX, transform.scaleY);
            if (status.equals(lastRenderStatus)) return;
            lastRenderStatus = status;
            preferences.edit().putString(PREF_RENDER_STATUS, status).apply();
            Log.i(TAG, status);
        }

        private void updateLightSensor() {
            boolean shouldRegister = visible && dayNight.isEnabled()
                    && dayNight.mode() == ScheduleMode.AUTO && lightSensor != null;
            if (shouldRegister && !sensorRegistered) {
                sensorRegistered = sensorManager.registerListener(lightListener, lightSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            } else if (!shouldRegister) {
                unregisterLightSensor();
            }
        }

        private void unregisterLightSensor() {
            if (sensorManager != null && sensorRegistered) {
                sensorManager.unregisterListener(lightListener);
            }
            sensorRegistered = false;
            lightTracker.reset();
        }

        private void releaseDecoded() {
            if (bitmap != null) bitmap.recycle();
            bitmap = null;
            movie = null;
            loadedFile = null;
        }
    }
}
