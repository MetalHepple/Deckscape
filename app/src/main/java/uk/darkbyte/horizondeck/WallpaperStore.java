package uk.darkbyte.horizondeck;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Movie;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class WallpaperStore {
    interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    private WallpaperStore() {}

    static File directory(Context context) {
        File directory = new File(context.getFilesDir(), "wallpapers");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create wallpaper library");
        }
        return directory;
    }

    static List<File> list(Context context) {
        File[] files = directory(context).listFiles(file -> file.isFile()
                && !file.getName().endsWith(".part")
                && WallpaperRules.isSupportedName(file.getName()));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, Comparator.comparing(File::getName));
        return new ArrayList<>(Arrays.asList(files));
    }

    static long totalBytes(Context context) {
        long total = 0;
        for (File file : list(context)) total += file.length();
        return total;
    }

    static File installedFile(Context context, RepositorySource source, CatalogItem item) {
        File candidate = new File(directory(context), destinationName(source, item));
        return candidate.isFile() && candidate.length() == item.size ? candidate : null;
    }

    static File install(Context context, RepositorySource source, CatalogItem item,
                        ProgressListener listener) throws IOException {
        if (!WallpaperRules.canInstall(item)) {
            throw new IOException("This file is unsupported or exceeds the safe size limit");
        }
        String downloadUrl = source.rawUrl(item.path);
        if (!WallpaperRules.isAllowedRawUrl(downloadUrl, source)) {
            throw new IOException("The repository produced an unsafe download URL");
        }

        File destination = new File(directory(context), destinationName(source, item));
        if (destination.isFile() && destination.length() == item.size) return destination;
        File partial = new File(destination.getAbsolutePath() + ".part");
        if (partial.exists() && !partial.delete()) throw new IOException("Unable to replace partial download");

        long maximum = WallpaperRules.maxBytesFor(item.name);
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(90_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "HorizonDeck/1.2");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download returned HTTP " + status);
            }
            if (!WallpaperRules.isAllowedRawUrl(connection.getURL().toString(), source)) {
                throw new IOException("Download redirected outside the selected GitHub repository");
            }
            long declared = connection.getContentLengthLong();
            if (declared > maximum) throw new IOException("Download is larger than the safe limit");

            long total = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                long lastUpdate = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maximum) throw new IOException("Download exceeded the safe limit");
                    output.write(buffer, 0, read);
                    if (listener != null && total - lastUpdate >= 256 * 1024) {
                        listener.onProgress(total, item.size);
                        lastUpdate = total;
                    }
                }
                output.getFD().sync();
            }
            if (item.size >= 0 && total != item.size) {
                throw new IOException("Downloaded size did not match GitHub metadata");
            }
            validateImage(partial, item.name);
            if (destination.exists() && !destination.delete()) {
                throw new IOException("Unable to replace the installed wallpaper");
            }
            if (!partial.renameTo(destination)) throw new IOException("Unable to finish the download");
            if (listener != null) listener.onProgress(total, total);
            return destination;
        } catch (IOException exception) {
            partial.delete();
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    static void select(Context context, File file) {
        context.getSharedPreferences(WallpaperEngineService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(WallpaperEngineService.PREF_CURRENT_FILE, file.getName())
                .putLong(WallpaperEngineService.PREF_LAST_SWITCH, System.currentTimeMillis())
                .apply();
    }

    static File current(Context context, List<File> files) {
        if (files.isEmpty()) return null;
        String selected = context.getSharedPreferences(WallpaperEngineService.PREFS,
                        Context.MODE_PRIVATE)
                .getString(WallpaperEngineService.PREF_CURRENT_FILE, "");
        for (File file : files) {
            if (file.getName().equals(selected)) return file;
        }
        return files.get(0);
    }

    static String destinationName(RepositorySource source, CatalogItem item) {
        String sourceName = WallpaperRules.safeFileName(source.owner + "_" + source.repository);
        String revision = item.sha.length() >= 12 ? item.sha.substring(0, 12)
                : Integer.toHexString(item.path.hashCode());
        return sourceName + "-" + revision + "-" + WallpaperRules.safeFileName(item.name);
    }

    private static void validateImage(File file, String name) throws IOException {
        int width;
        int height;
        if (WallpaperRules.isGif(name)) {
            Movie movie = Movie.decodeFile(file.getAbsolutePath());
            width = movie == null ? 0 : movie.width();
            height = movie == null ? 0 : movie.height();
        } else {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            width = bounds.outWidth;
            height = bounds.outHeight;
        }
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0) throw new IOException("Downloaded file is not a decodable image");
        if (width > 16_384 || height > 16_384 || pixels > 120_000_000L) {
            throw new IOException("Image dimensions exceed the decoder safety limit");
        }
    }
}
