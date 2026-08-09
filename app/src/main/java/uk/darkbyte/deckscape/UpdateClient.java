package uk.darkbyte.deckscape;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Performs bounded HTTPS reads against Deckscape's fixed GitHub release location. */
final class UpdateClient {
    interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    static final String UPDATE_FILE_NAME = "deckscape-update.apk";

    private final File directory;

    UpdateClient(Context context) {
        directory = new File(context.getCacheDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create the update cache");
        }
    }

    File updateFile() {
        return new File(directory, UPDATE_FILE_NAME);
    }

    /** Reads and validates GitHub's latest stable release metadata. */
    UpdateRelease latest() throws IOException {
        if (!UpdateRules.isAllowedApiUrl(UpdateRules.API_URL)) {
            throw new IOException("The update API URL is invalid");
        }
        HttpURLConnection connection = connection(new URL(UpdateRules.API_URL),
                "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String suffix = status == 403 || status == 429
                        ? " • GitHub rate limit reached" : "";
                throw new IOException("GitHub update check returned HTTP " + status + suffix);
            }
            if (!UpdateRules.isAllowedApiUrl(connection.getURL().toString())) {
                throw new IOException("GitHub redirected the update API unexpectedly");
            }
            return UpdateRelease.parse(readText(connection, UpdateRules.MAX_RELEASE_JSON_BYTES));
        } finally {
            connection.disconnect();
        }
    }

    /** Downloads the release APK atomically and verifies its published SHA-256 digest. */
    File download(UpdateRelease release, ProgressListener listener) throws IOException {
        if (release.apk == null) throw new IOException("Release download metadata is missing");
        String expected = release.sha256 != null
                ? release.sha256 : readChecksum(release.checksum);
        if (expected == null) throw new IOException("The release checksum is invalid");

        File destination = updateFile();
        File partial = new File(directory, UPDATE_FILE_NAME + ".part");
        if (partial.exists() && !partial.delete()) {
            throw new IOException("Unable to replace the partial update");
        }
        HttpURLConnection connection = openReleaseAsset(release.apk.url);
        try {
            long declared = connection.getContentLengthLong();
            if (declared > UpdateRules.MAX_APK_BYTES || declared > release.apk.size) {
                throw new IOException("The update download exceeds its declared size");
            }
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (Exception exception) {
                throw new IOException("SHA-256 is unavailable", exception);
            }
            long total = 0L;
            long lastUpdate = 0L;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Update download cancelled");
                    }
                    total += read;
                    if (total > UpdateRules.MAX_APK_BYTES || total > release.apk.size) {
                        throw new IOException("The update download exceeded its size limit");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    if (listener != null && total - lastUpdate >= 64 * 1024) {
                        listener.onProgress(total, release.apk.size);
                        lastUpdate = total;
                    }
                }
                output.getFD().sync();
            }
            if (total != release.apk.size) {
                throw new IOException("The update download was incomplete");
            }
            String actual = toHex(digest.digest());
            if (!expected.equalsIgnoreCase(actual)) {
                throw new IOException("The update checksum did not match GitHub");
            }
            if (destination.exists() && !destination.delete()) {
                throw new IOException("Unable to replace the cached update");
            }
            if (!partial.renameTo(destination)) {
                throw new IOException("Unable to finish the update download");
            }
            if (listener != null) listener.onProgress(total, total);
            return destination;
        } catch (IOException exception) {
            partial.delete();
            throw exception;
        } finally {
            connection.disconnect();
        }
    }

    private String readChecksum(UpdateRelease.Asset asset) throws IOException {
        if (asset == null) return null;
        HttpURLConnection connection = openReleaseAsset(asset.url);
        try {
            String checksum = readText(connection, UpdateRules.MAX_CHECKSUM_BYTES);
            return UpdateRules.sha256(checksum);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openReleaseAsset(String value) throws IOException {
        if (!UpdateRules.isAllowedReleaseUrl(value)) {
            throw new IOException("The release download URL is not allowed");
        }
        URL current = new URL(value);
        for (int redirect = 0; redirect <= 5; redirect++) {
            HttpURLConnection connection = connection(current, "application/octet-stream");
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                if (!UpdateRules.isAllowedReleaseUrl(connection.getURL().toString())) {
                    connection.disconnect();
                    throw new IOException("The release redirected outside GitHub");
                }
                return connection;
            }
            if (!isRedirect(status)) {
                connection.disconnect();
                throw new IOException("GitHub release download returned HTTP " + status);
            }
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null) throw new IOException("GitHub returned an empty redirect");
            current = new URL(current, location);
            if (!UpdateRules.isAllowedReleaseUrl(current.toString())) {
                throw new IOException("The release redirected outside GitHub");
            }
        }
        throw new IOException("The release download redirected too many times");
    }

    private static HttpURLConnection connection(URL url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(90_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", AppMetadata.userAgent());
        return connection;
    }

    private static String readText(HttpURLConnection connection, long maximum) throws IOException {
        long declared = connection.getContentLengthLong();
        if (declared > maximum) throw new IOException("The update response is too large");
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximum) throw new IOException("The update response exceeded its limit");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    private static String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte item : value) builder.append(String.format(Locale.ROOT, "%02X", item));
        return builder.toString();
    }
}
