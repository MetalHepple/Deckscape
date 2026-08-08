package uk.darkbyte.horizondeck;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PreviewCache {
    interface Callback {
        void onResult(Bitmap bitmap, String error);
    }

    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;
    private static final long MAX_DISK_BYTES = 96L * 1024 * 1024;
    private static final long PRUNE_TO_BYTES = 72L * 1024 * 1024;
    private static final long MAX_PROXY_BYTES = 2L * 1024 * 1024;
    private static final String PREFERENCES = "preview_settings";
    private static final String KEY_DATA_SAVER = "data_saver";

    private final File directory;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory;
    private final Map<String, List<Callback>> pending = new HashMap<>();
    private final SharedPreferences preferences;
    private volatile boolean dataSaverEnabled;

    PreviewCache(Context context) {
        directory = new File(context.getCacheDir(), "wallpaper-previews");
        if (!directory.exists()) directory.mkdirs();
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        dataSaverEnabled = preferences.getBoolean(KEY_DATA_SAVER, true);
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClassMb = manager == null ? 128 : manager.getMemoryClass();
        int cacheKb = Math.min(32 * 1024, memoryClassMb * 1024 / 8);
        memory = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
    }

    void request(RepositorySource source, CatalogItem item, Callback callback) {
        String key = item.stableKey(source);
        Bitmap inMemory = memory.get(key);
        if (inMemory != null && !inMemory.isRecycled()) {
            callback.onResult(inMemory, null);
            return;
        }
        synchronized (pending) {
            List<Callback> waiting = pending.get(key);
            if (waiting != null) {
                waiting.add(callback);
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(callback);
            pending.put(key, waiting);
        }
        executor.execute(() -> {
            try {
                Bitmap bitmap = loadOrCreate(source, item, key);
                memory.put(key, bitmap);
                main.post(() -> deliver(key, bitmap, null));
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "Preview unavailable" : exception.getMessage();
                main.post(() -> deliver(key, null, message));
            }
        });
    }

    private void deliver(String key, Bitmap bitmap, String error) {
        List<Callback> callbacks;
        synchronized (pending) {
            callbacks = pending.remove(key);
        }
        if (callbacks == null) return;
        for (Callback callback : callbacks) callback.onResult(bitmap, error);
    }

    long diskBytes() {
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".jpg"));
        if (files == null) return 0;
        long total = 0;
        for (File file : files) total += file.length();
        return total;
    }

    void clear() {
        memory.evictAll();
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) file.delete();
    }

    void close() {
        executor.shutdownNow();
    }

    boolean isDataSaverEnabled() {
        return dataSaverEnabled;
    }

    void setDataSaverEnabled(boolean enabled) {
        dataSaverEnabled = enabled;
        preferences.edit().putBoolean(KEY_DATA_SAVER, enabled).apply();
    }

    private Bitmap loadOrCreate(RepositorySource source, CatalogItem item, String key) throws IOException {
        File cached = new File(directory, sha256(key) + ".jpg");
        if (cached.isFile()) {
            Bitmap decoded = BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (decoded != null) {
                cached.setLastModified(System.currentTimeMillis());
                return decoded;
            }
            cached.delete();
        }
        File original = new File(directory, sha256(key) + ".source.part");
        Bitmap thumbnail = null;
        IOException proxyFailure = null;
        if (dataSaverEnabled) {
            try {
                downloadProxyPreview(source, item, original);
                thumbnail = createThumbnail(original, "preview.jpg");
            } catch (IOException exception) {
                proxyFailure = exception;
                original.delete();
            }
        }
        if (thumbnail == null) {
            long cap = WallpaperRules.isGif(item.name)
                    ? WallpaperRules.MAX_GIF_BYTES : WallpaperRules.MAX_PREVIEW_FETCH_BYTES;
            if (item.size < 0 || item.size > cap) {
                String suffix = proxyFailure == null ? "" : " • data saver unavailable";
                throw new IOException("Preview skipped • " + item.humanSize() + suffix);
            }
            try {
                downloadPreviewSource(source, item, original, cap);
                thumbnail = createThumbnail(original, item.name);
            } finally {
                original.delete();
            }
        }
        original.delete();
        File partial = new File(cached.getAbsolutePath() + ".part");
        try (FileOutputStream output = new FileOutputStream(partial)) {
            if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, 84, output)) {
                throw new IOException("Unable to encode preview");
            }
            output.getFD().sync();
        }
        if (cached.exists() && !cached.delete()) throw new IOException("Unable to refresh preview");
        if (!partial.renameTo(cached)) throw new IOException("Unable to finish preview");
        prune();
        return thumbnail;
    }

    private static void downloadProxyPreview(RepositorySource source, CatalogItem item,
                                             File destination) throws IOException {
        String url = PreviewUrl.forItem(source, item);
        if (!PreviewUrl.isAllowedEndpoint(url)) throw new IOException("Unsafe preview-service URL");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(45_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "HorizonDeck/1.2");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Preview service returned HTTP " + status);
            }
            if (!PreviewUrl.isAllowedEndpoint(connection.getURL().toString())) {
                throw new IOException("Preview service redirected outside wsrv.nl");
            }
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IOException("Preview service returned non-image data");
            }
            long declared = connection.getContentLengthLong();
            if (declared > MAX_PROXY_BYTES) throw new IOException("Preview response is too large");
            copyBounded(connection, destination, MAX_PROXY_BYTES, false, item);
        } catch (IOException exception) {
            destination.delete();
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadPreviewSource(RepositorySource source, CatalogItem item,
                                              File destination, long maximum) throws IOException {
        String url = source.rawUrl(item.path);
        if (!WallpaperRules.isAllowedRawUrl(url, source)) throw new IOException("Unsafe preview URL");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "HorizonDeck/1.2");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Preview returned HTTP " + status);
            }
            if (!WallpaperRules.isAllowedRawUrl(connection.getURL().toString(), source)) {
                throw new IOException("Preview redirected outside GitHub");
            }
            long declared = connection.getContentLengthLong();
            if (declared > maximum) throw new IOException("Preview source is too large");
            copyBounded(connection, destination, maximum, true, item);
        } catch (IOException exception) {
            destination.delete();
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private static void copyBounded(HttpURLConnection connection, File destination, long maximum,
                                    boolean requireCatalogSize, CatalogItem item) throws IOException {
        long total = 0;
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximum) throw new IOException("Preview response exceeded the limit");
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
        if (requireCatalogSize && item.size >= 0 && total != item.size) {
            throw new IOException("Incomplete preview source");
        }
    }

    private static Bitmap createThumbnail(File source, String name) throws IOException {
        if (WallpaperRules.isGif(name)) {
            Movie movie = Movie.decodeFile(source.getAbsolutePath());
            if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
                throw new IOException("GIF preview could not be decoded");
            }
            Bitmap result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.drawColor(Color.rgb(7, 17, 27));
            movie.setTime(Math.min(250, Math.max(0, movie.duration() - 1)));
            float scale = Math.max(WIDTH / (float) movie.width(), HEIGHT / (float) movie.height());
            canvas.save();
            canvas.translate((WIDTH - movie.width() * scale) / 2f,
                    (HEIGHT - movie.height() * scale) / 2f);
            canvas.scale(scale, scale);
            movie.draw(canvas, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            canvas.restore();
            return result;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        long pixels = (long) bounds.outWidth * bounds.outHeight;
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixels > 120_000_000L) {
            throw new IOException("Image preview dimensions are invalid");
        }
        int sample = 1;
        while (bounds.outWidth / sample > WIDTH * 2 || bounds.outHeight / sample > HEIGHT * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (decoded == null) throw new IOException("Image preview could not be decoded");
        Bitmap result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.rgb(7, 17, 27));
        float scale = Math.max(WIDTH / (float) decoded.getWidth(), HEIGHT / (float) decoded.getHeight());
        canvas.save();
        canvas.translate((WIDTH - decoded.getWidth() * scale) / 2f,
                (HEIGHT - decoded.getHeight() * scale) / 2f);
        canvas.scale(scale, scale);
        canvas.drawBitmap(decoded, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        canvas.restore();
        decoded.recycle();
        return result;
    }

    private void prune() {
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".jpg"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_DISK_BYTES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= PRUNE_TO_BYTES) break;
            long size = file.length();
            if (file.delete()) total -= size;
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) builder.append(String.format(Locale.ROOT, "%02x", item));
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
