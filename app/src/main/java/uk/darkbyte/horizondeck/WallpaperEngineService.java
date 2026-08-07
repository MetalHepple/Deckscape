package uk.darkbyte.horizondeck;

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

public final class WallpaperEngineService extends WallpaperService {
    private static final String TAG = "HorizonDeck";
    static final String ACTION_NEXT = "uk.darkbyte.horizondeck.NEXT";
    static final String ACTION_LIBRARY_CHANGED = "uk.darkbyte.horizondeck.LIBRARY_CHANGED";
    static final String PREFS = "wallpaper_preferences";
    static final String PREF_INTERVAL = "rotation_interval_ms";
    static final String PREF_CURRENT_FILE = "current_file";
    static final String PREF_LAST_SWITCH = "last_switch_ms";
    static final String PREF_DECODE_STATUS = "last_decode_status";
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
        private boolean visible;
        private boolean receiverRegistered;
        private int surfaceWidth;
        private int surfaceHeight;
        private File loadedFile;
        private Bitmap bitmap;
        private Movie movie;
        private long movieStartUptime;

        private final BroadcastReceiver commands = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_NEXT.equals(intent.getAction())) nextWallpaper();
                else {
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
            if (!visible || surfaceWidth <= 0 || surfaceHeight <= 0) return;
            List<File> files = WallpaperStore.list(WallpaperEngineService.this);
            if (files.isEmpty()) {
                drawFallback("Choose a wallpaper in HorizonDeck");
                return;
            }

            long now = System.currentTimeMillis();
            long lastSwitch = preferences.getLong(PREF_LAST_SWITCH, 0);
            long interval = preferences.getLong(PREF_INTERVAL, DEFAULT_INTERVAL);
            File selected = WallpaperStore.current(WallpaperEngineService.this, files);
            int index = Math.max(0, files.indexOf(selected));
            if (lastSwitch == 0) {
                preferences.edit().putLong(PREF_LAST_SWITCH, now).apply();
            } else if (RotationPolicy.shouldRotate(now, lastSwitch, interval, files.size())) {
                index = RotationPolicy.nextIndex(index, files.size());
                selected = files.get(index);
                preferences.edit()
                        .putString(PREF_CURRENT_FILE, selected.getName())
                        .putLong(PREF_LAST_SWITCH, now)
                        .apply();
                releaseDecoded();
            }

            if (!selected.equals(loadedFile)) load(selected);
            drawLoaded(selected.getName());
            handler.removeCallbacks(drawRunnable);
            if (visible) handler.postDelayed(drawRunnable, movie != null ? 100L : 15_000L);
        }

        private void nextWallpaper() {
            List<File> files = WallpaperStore.list(WallpaperEngineService.this);
            if (files.isEmpty()) return;
            File selected = WallpaperStore.current(WallpaperEngineService.this, files);
            int next = RotationPolicy.nextIndex(Math.max(0, files.indexOf(selected)), files.size());
            preferences.edit()
                    .putString(PREF_CURRENT_FILE, files.get(next).getName())
                    .putLong(PREF_LAST_SWITCH, System.currentTimeMillis())
                    .apply();
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
                if (movie != null && movie.width() > 0 && movie.height() > 0) {
                    int duration = movie.duration() > 0 ? movie.duration() : 1_000;
                    movie.setTime((int) ((SystemClock.uptimeMillis() - movieStartUptime) % duration));
                    float scale = Math.max(surfaceWidth / (float) movie.width(),
                            surfaceHeight / (float) movie.height());
                    float left = (surfaceWidth - movie.width() * scale) / 2f;
                    float top = (surfaceHeight - movie.height() * scale) / 2f;
                    canvas.save();
                    canvas.translate(left, top);
                    canvas.scale(scale, scale);
                    movie.draw(canvas, 0, 0, bitmapPaint);
                    canvas.restore();
                } else if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                    float scale = Math.max(surfaceWidth / (float) bitmap.getWidth(),
                            surfaceHeight / (float) bitmap.getHeight());
                    float left = (surfaceWidth - bitmap.getWidth() * scale) / 2f;
                    float top = (surfaceHeight - bitmap.getHeight() * scale) / 2f;
                    canvas.save();
                    canvas.translate(left, top);
                    canvas.scale(scale, scale);
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                    canvas.restore();
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
            canvas.drawText(message, surfaceWidth / 2f, surfaceHeight / 2f, paint);
        }

        private void releaseDecoded() {
            if (bitmap != null) bitmap.recycle();
            bitmap = null;
            movie = null;
            loadedFile = null;
        }
    }
}
