package uk.darkbyte.deckscape;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads bounded, cacheable public metadata for About, Contributors, and Licences. */
final class GitHubMetadataClient {
    private static final String API_BASE = "https://api.github.com";
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final int MAX_CONTRIBUTORS = 24;
    private static final int MAX_AVATAR_BYTES = 512 * 1024;
    private static final int MAX_AVATAR_DIMENSION = 1_024;
    private static final int AVATAR_DECODE_TARGET = 128;
    private static final long AVATAR_CACHE_MAX_BYTES = 4L * 1024 * 1024;
    private static final long AVATAR_CACHE_TARGET_BYTES = 3L * 1024 * 1024;
    private static final long CONTRIBUTORS_FRESH_MS = 24L * 60 * 60 * 1000;
    private static final long LICENSE_FRESH_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long AVATAR_FRESH_MS = 7L * 24 * 60 * 60 * 1000;

    private final File cacheDirectory;

    GitHubMetadataClient(android.content.Context context) {
        cacheDirectory = new File(context.getCacheDir(), "github-metadata");
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()
                && !cacheDirectory.isDirectory()) {
            throw new IllegalStateException("Unable to create the metadata cache");
        }
    }

    /** Loads contributors and one repository-level licence summary per configured source. */
    RepositoryMetadata load(List<RepositorySource> sources) {
        boolean stale = false;
        List<RepositoryMetadata.Contributor> contributors = new ArrayList<>();
        try {
            CachedJson response = readJson(API_BASE
                    + "/repos/MetalHepple/Deckscape/contributors?per_page=100&anon=1",
                    CONTRIBUTORS_FRESH_MS);
            contributors = parseContributors(response.json);
            stale |= response.stale;
        } catch (Exception ignored) {
            // The UI always retains the bundled creator credit when GitHub is unavailable.
        }

        Map<String, RepositoryMetadata.SourceLicense> licenses = new LinkedHashMap<>();
        for (RepositorySource source : sources) {
            String key = RepositoryMetadata.repositoryKey(source.owner, source.repository);
            if (licenses.containsKey(key)) continue;
            String repositoryUrl = source.repositoryUrl();
            try {
                CachedJson response = readJson(API_BASE + "/repos/" + source.owner
                        + "/" + source.repository, LICENSE_FRESH_MS);
                licenses.put(key, parseLicense(response.json, source.owner,
                        source.repository, repositoryUrl));
                stale |= response.stale;
            } catch (Exception ignored) {
                licenses.put(key, new RepositoryMetadata.SourceLicense(
                        source.owner + "/" + source.repository,
                        "Licence metadata unavailable", "", repositoryUrl, false));
            }
        }
        return new RepositoryMetadata(contributors, licenses, stale);
    }

    static List<RepositoryMetadata.Contributor> parseContributors(String json) throws Exception {
        JSONArray array = new JSONArray(json);
        List<RepositoryMetadata.Contributor> parsed = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            String login = item.optString("login", "").trim();
            String name = item.optString("name", "").trim();
            String displayName = name.isEmpty() ? login : name;
            String type = item.optString("type", "");
            if (displayName.isEmpty() || "Bot".equalsIgnoreCase(type)
                    || login.toLowerCase(Locale.ROOT).endsWith("[bot]")) continue;
            String page = item.optString("html_url", "");
            if (!isAllowedGitHubPage(page)) {
                page = login.isEmpty() ? "" : "https://github.com/" + login;
            }
            String avatar = item.optString("avatar_url", "");
            if (!isAllowedAvatarUrl(avatar)) avatar = "";
            parsed.add(new RepositoryMetadata.Contributor(displayName, login, page, avatar));
        }
        List<RepositoryMetadata.Contributor> merged = mergeCreatorAliases(parsed);
        if (merged.size() <= MAX_CONTRIBUTORS) return merged;
        return new ArrayList<>(merged.subList(0, MAX_CONTRIBUTORS));
    }

    /** Returns a validated avatar from the bounded disk cache or GitHub's avatar host. */
    Bitmap loadAvatar(RepositoryMetadata.Contributor contributor) {
        if (contributor == null || !isAllowedAvatarUrl(contributor.avatarUrl)) return null;
        File cache = new File(cacheDirectory, sha256(contributor.avatarUrl) + ".avatar");
        long age = cache.isFile() ? System.currentTimeMillis() - cache.lastModified()
                : Long.MAX_VALUE;
        if (age >= 0 && age < AVATAR_FRESH_MS) {
            Bitmap cached = decodeAvatar(readBytesQuietly(cache));
            if (cached != null) return cached;
        }
        try {
            byte[] encoded = requestAvatar(contributor.avatarUrl);
            Bitmap bitmap = decodeAvatar(encoded);
            if (bitmap == null) throw new IOException("GitHub avatar could not be decoded");
            writeBytes(cache, encoded);
            pruneAvatarCache();
            return bitmap;
        } catch (IOException | RuntimeException ignored) {
            return decodeAvatar(readBytesQuietly(cache));
        }
    }

    private static List<RepositoryMetadata.Contributor> mergeCreatorAliases(
            List<RepositoryMetadata.Contributor> contributors) {
        RepositoryMetadata.Contributor account = null;
        boolean foundCreator = false;
        for (RepositoryMetadata.Contributor contributor : contributors) {
            boolean creatorAccount = AppMetadata.CREATOR_LOGIN.equalsIgnoreCase(contributor.login);
            boolean creatorName = contributor.login.isEmpty()
                    && AppMetadata.CREATOR_NAME.equalsIgnoreCase(contributor.displayName);
            if (!creatorAccount && !creatorName) continue;
            foundCreator = true;
            if (creatorAccount) account = contributor;
        }
        if (!foundCreator) return contributors;

        String page = account == null ? "https://github.com/" + AppMetadata.CREATOR_LOGIN
                : account.pageUrl;
        String avatar = account == null || account.avatarUrl.isEmpty()
                ? AppMetadata.CREATOR_AVATAR_URL : account.avatarUrl;
        RepositoryMetadata.Contributor merged = new RepositoryMetadata.Contributor(
                AppMetadata.CREATOR_NAME, AppMetadata.CREATOR_LOGIN, page, avatar);
        List<RepositoryMetadata.Contributor> result = new ArrayList<>();
        result.add(merged);
        for (RepositoryMetadata.Contributor contributor : contributors) {
            boolean creatorAccount = AppMetadata.CREATOR_LOGIN.equalsIgnoreCase(contributor.login);
            boolean creatorName = contributor.login.isEmpty()
                    && AppMetadata.CREATOR_NAME.equalsIgnoreCase(contributor.displayName);
            if (!creatorAccount && !creatorName) result.add(contributor);
        }
        return result;
    }

    static RepositoryMetadata.SourceLicense parseLicense(String json, String owner,
                                                          String repository,
                                                          String repositoryUrl) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject license = root.optJSONObject("license");
        String repositoryName = owner + "/" + repository;
        if (license == null || license.optString("key", "").isEmpty()) {
            return new RepositoryMetadata.SourceLicense(repositoryName,
                    "No repository licence detected", "", repositoryUrl, false);
        }
        String name = license.optString("name", "Repository licence");
        String spdx = license.optString("spdx_id", "");
        if ("NOASSERTION".equalsIgnoreCase(spdx)) spdx = "";
        String page = license.optString("html_url", repositoryUrl);
        if (!isAllowedGitHubPage(page)) page = repositoryUrl;
        return new RepositoryMetadata.SourceLicense(repositoryName, name, spdx, page, true);
    }

    private CachedJson readJson(String url, long freshMillis) throws IOException {
        if (!isAllowedApiUrl(url)) throw new IOException("Unsafe GitHub API URL");
        File cache = new File(cacheDirectory, sha256(url) + ".json");
        long age = cache.isFile() ? System.currentTimeMillis() - cache.lastModified()
                : Long.MAX_VALUE;
        if (age >= 0 && age < freshMillis) return new CachedJson(readFile(cache), false);
        try {
            String json = requestJson(url);
            writeFile(cache, json);
            return new CachedJson(json, false);
        } catch (IOException exception) {
            if (cache.isFile()) return new CachedJson(readFile(cache), true);
            throw exception;
        }
    }

    private static String requestJson(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", AppMetadata.userAgent());
        try {
            int status = connection.getResponseCode();
            if (!isAllowedApiUrl(connection.getURL().toString())) {
                throw new IOException("GitHub API redirected outside api.github.com");
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub returned HTTP " + status);
            }
            return readUtf8(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] requestAvatar(String url) throws IOException {
        if (!isAllowedAvatarUrl(url)) throw new IOException("Unsafe GitHub avatar URL");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "image/*");
        connection.setRequestProperty("User-Agent", AppMetadata.userAgent());
        try {
            int status = connection.getResponseCode();
            if (!isAllowedAvatarUrl(connection.getURL().toString())) {
                throw new IOException("GitHub avatar redirected outside its image host");
            }
            String type = connection.getContentType();
            if (status != HttpURLConnection.HTTP_OK || type == null
                    || !type.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IOException("GitHub returned an invalid avatar response");
            }
            int declared = connection.getContentLength();
            if (declared > MAX_AVATAR_BYTES) throw new IOException("GitHub avatar is too large");
            return readBytes(connection.getInputStream(), MAX_AVATAR_BYTES);
        } finally {
            connection.disconnect();
        }
    }

    private static String readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return readUtf8(input);
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        try (InputStream closeable = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = closeable.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("Metadata is too large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static byte[] readBytes(InputStream input, int maximumBytes) throws IOException {
        try (InputStream closeable = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = closeable.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) throw new IOException("Image is too large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] readBytesQuietly(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_AVATAR_BYTES) return null;
        try {
            return readBytes(new FileInputStream(file), MAX_AVATAR_BYTES);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Bitmap decodeAvatar(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_AVATAR_BYTES) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1
                || bounds.outWidth > MAX_AVATAR_DIMENSION
                || bounds.outHeight > MAX_AVATAR_DIMENSION) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / (options.inSampleSize * 2) >= AVATAR_DECODE_TARGET
                && bounds.outHeight / (options.inSampleSize * 2) >= AVATAR_DECODE_TARGET) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
    }

    private static void writeFile(File file, String value) throws IOException {
        File partial = new File(file.getAbsolutePath() + ".part");
        try {
            try (FileOutputStream output = new FileOutputStream(partial)) {
                output.write(value.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (file.exists() && !file.delete()) {
                throw new IOException("Unable to refresh metadata");
            }
            if (!partial.renameTo(file)) throw new IOException("Unable to finish metadata cache");
        } catch (IOException exception) {
            partial.delete();
            throw exception;
        }
    }

    private static void writeBytes(File file, byte[] value) throws IOException {
        File partial = new File(file.getAbsolutePath() + ".part");
        try {
            try (FileOutputStream output = new FileOutputStream(partial)) {
                output.write(value);
                output.getFD().sync();
            }
            if (file.exists() && !file.delete()) {
                throw new IOException("Unable to refresh avatar metadata");
            }
            if (!partial.renameTo(file)) throw new IOException("Unable to finish avatar cache");
        } catch (IOException exception) {
            partial.delete();
            throw exception;
        }
    }

    private void pruneAvatarCache() {
        File[] files = cacheDirectory.listFiles((directory, name) -> name.endsWith(".avatar"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += Math.max(0, file.length());
        if (total <= AVATAR_CACHE_MAX_BYTES) return;
        java.util.Arrays.sort(files, (left, right) ->
                Long.compare(left.lastModified(), right.lastModified()));
        for (File file : files) {
            long length = Math.max(0, file.length());
            if (file.delete()) total -= length;
            if (total <= AVATAR_CACHE_TARGET_BYTES) break;
        }
    }

    private static boolean isAllowedApiUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "api.github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isAllowedGitHubPage(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean isAllowedAvatarUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "avatars.githubusercontent.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawPath() != null
                    && uri.getRawPath().matches("/u/[0-9]+/?");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format(Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CachedJson {
        final String json;
        final boolean stale;

        CachedJson(String json, boolean stale) {
            this.json = json;
            this.stale = stale;
        }
    }
}
