package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Bitmap;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns Deckscape's private downloaded-wallpaper library and validated install pipeline. */
final class WallpaperStore {
    private static final String PREF_EXCLUDED_FILES = "excluded_slideshow_files";

    /** Receives throttled byte progress while a wallpaper is downloaded. */
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

    /** Returns every valid wallpaper downloaded into Deckscape's private library. */
    static List<File> listDownloaded(Context context) {
        File[] files = directory(context).listFiles(file -> file.isFile()
                && !file.getName().endsWith(".part")
                && WallpaperRules.isSupportedName(file.getName()));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, Comparator.comparing(File::getName));
        return new ArrayList<>(Arrays.asList(files));
    }

    /** Returns only downloaded wallpapers currently included in timed rotation. */
    static List<File> list(Context context) {
        Set<String> excluded = excludedNames(context);
        List<File> included = listDownloaded(context);
        included.removeIf(file -> excluded.contains(file.getName()));
        return included;
    }

    static long totalBytes(Context context) {
        long total = 0;
        for (File file : listDownloaded(context)) total += file.length();
        return total;
    }

    static File installedFile(Context context, RepositorySource source, CatalogItem item) {
        File candidate = new File(directory(context), destinationName(source, item));
        return candidate.isFile() && candidate.length() == item.size ? candidate : null;
    }

    /** Downloads, bounds-checks, decodes, and atomically installs one catalog item. */
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
        connection.setRequestProperty("User-Agent", AppMetadata.userAgent());
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

    /** Returns whether an installed wallpaper participates in the slideshow. */
    static boolean isIncluded(Context context, File file) {
        return file != null && file.isFile() && !excludedNames(context).contains(file.getName());
    }

    /** Adds a downloaded wallpaper to rotation without necessarily changing the current image. */
    static File include(Context context, File file) throws IOException {
        File installed = requireLibraryFile(context, file);
        Set<String> excluded = excludedNames(context);
        if (excluded.remove(installed.getName())) saveExcludedNames(context, excluded);
        return ensureCurrentSelection(context);
    }

    /** Removes a wallpaper from rotation while retaining its downloaded file. */
    static File removeFromSlideshow(Context context, File file) throws IOException {
        File installed = requireLibraryFile(context, file);
        Set<String> excluded = excludedNames(context);
        if (excluded.add(installed.getName())) saveExcludedNames(context, excluded);
        return ensureCurrentSelection(context);
    }

    /** Permanently deletes one validated library file and repairs slideshow selection state. */
    static File delete(Context context, File file) throws IOException {
        File installed = requireLibraryFile(context, file);
        if (!installed.delete()) throw new IOException("Unable to delete the wallpaper");
        new WallpaperProfileStore(context).remove(installed);
        Set<String> excluded = excludedNames(context);
        if (excluded.remove(installed.getName())) saveExcludedNames(context, excluded);
        return ensureCurrentSelection(context);
    }

    /** Selects an installed file as an explicit user override. */
    static void select(Context context, File file) {
        select(context, file, true);
    }

    /** Selects a file as part of automatic rotation or schedule repair. */
    static void selectForEngine(Context context, File file) {
        select(context, file, false);
    }

    private static void select(Context context, File file, boolean manualOverride) {
        context.getSharedPreferences(WallpaperEngineService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(WallpaperEngineService.PREF_CURRENT_FILE, file.getName())
                .putLong(WallpaperEngineService.PREF_LAST_SWITCH, System.currentTimeMillis())
                .putBoolean(WallpaperEngineService.PREF_MANUAL_OVERRIDE, manualOverride)
                .apply();
    }

    /** Returns the selected file from the complete downloaded library, if it still exists. */
    static File selectedDownloaded(Context context) {
        String selected = context.getSharedPreferences(WallpaperEngineService.PREFS,
                        Context.MODE_PRIVATE)
                .getString(WallpaperEngineService.PREF_CURRENT_FILE, "");
        for (File file : listDownloaded(context)) {
            if (file.getName().equals(selected)) return file;
        }
        return null;
    }

    /** Chooses the next eligible file, preferring a wallpaper specific to the new period. */
    static File nextForPhase(Context context, File current, DayPhase phase) {
        List<File> eligible = new DayNightSettings(context).eligibleFiles(phase);
        if (eligible.isEmpty()) return null;
        WallpaperProfileStore profiles = new WallpaperProfileStore(context);
        int start = current == null ? -1 : eligible.indexOf(current);
        for (int offset = 1; offset <= eligible.size(); offset++) {
            File candidate = eligible.get(Math.floorMod(start + offset, eligible.size()));
            if (profiles.get(candidate).role != DayNightRole.BOTH) return candidate;
        }
        return current != null && eligible.contains(current) ? current
                : eligible.get(Math.floorMod(start + 1, eligible.size()));
    }

    /** Resolves the selected file, falling back to the first installed wallpaper. */
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

    /** Returns a stable collision-resistant filename for a repository revision. */
    static String destinationName(RepositorySource source, CatalogItem item) {
        String sourceName = WallpaperRules.safeFileName(source.owner + "_" + source.repository);
        String revision = item.sha.length() >= 12 ? item.sha.substring(0, 12)
                : Integer.toHexString(item.path.hashCode());
        return sourceName + "-" + revision + "-" + WallpaperRules.safeFileName(item.name);
    }

    /** Returns a readable title from Deckscape's collision-resistant stored filename. */
    static String displayName(File file) {
        String stored = file.getName();
        for (int index = 0; index + 14 < stored.length(); index++) {
            if (stored.charAt(index) != '-' || stored.charAt(index + 13) != '-') continue;
            boolean revision = true;
            for (int offset = 1; offset <= 12; offset++) {
                char value = Character.toLowerCase(stored.charAt(index + offset));
                if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                    revision = false;
                    break;
                }
            }
            if (revision) return displayName(stored.substring(index + 14));
        }
        return displayName(stored);
    }

    /** Returns a catalog filename without its path, final extension, or underscore separators. */
    static String displayName(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String filename = slash >= 0 ? name.substring(slash + 1) : name;
        return withoutExtension(filename).replace('_', ' ');
    }

    private static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Set<String> excludedNames(Context context) {
        Set<String> saved = context.getSharedPreferences(WallpaperEngineService.PREFS,
                        Context.MODE_PRIVATE)
                .getStringSet(PREF_EXCLUDED_FILES, null);
        return saved == null ? new HashSet<>() : new HashSet<>(saved);
    }

    private static void saveExcludedNames(Context context, Set<String> names) {
        context.getSharedPreferences(WallpaperEngineService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_EXCLUDED_FILES, new HashSet<>(names))
                .apply();
    }

    private static File ensureCurrentSelection(Context context) {
        List<File> included = list(context);
        String selected = context.getSharedPreferences(WallpaperEngineService.PREFS,
                        Context.MODE_PRIVATE)
                .getString(WallpaperEngineService.PREF_CURRENT_FILE, "");
        for (File candidate : included) {
            if (candidate.getName().equals(selected)) return candidate;
        }
        if (!included.isEmpty()) {
            File fallback = included.get(0);
            selectForEngine(context, fallback);
            return fallback;
        }
        context.getSharedPreferences(WallpaperEngineService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(WallpaperEngineService.PREF_CURRENT_FILE)
                .putLong(WallpaperEngineService.PREF_LAST_SWITCH, System.currentTimeMillis())
                .putBoolean(WallpaperEngineService.PREF_MANUAL_OVERRIDE, false)
                .apply();
        return null;
    }

    private static File requireLibraryFile(Context context, File file) throws IOException {
        if (file == null) throw new IOException("Wallpaper is unavailable");
        File library = directory(context).getCanonicalFile();
        File candidate = file.getCanonicalFile();
        if (!library.equals(candidate.getParentFile())
                || !candidate.isFile()
                || !WallpaperRules.isSupportedName(candidate.getName())) {
            throw new IOException("Wallpaper is outside Deckscape's library");
        }
        return candidate;
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
        if (!WallpaperRules.isGif(name)) {
            int sample = 1;
            while (width / sample > 2_048 || height / sample > 2_048) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (decoded == null) throw new IOException("Downloaded image data could not be decoded");
            decoded.recycle();
        }
    }
}
