package uk.darkbyte.horizondeck;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class GitHubCatalogClient {
    private static final String API_BASE = "https://api.github.com";
    private static final int MAX_JSON_BYTES = 12 * 1024 * 1024;
    private static final int MAX_ALL_ITEMS = 5_000;
    private static final long CACHE_FRESH_MS = 2L * 60 * 60 * 1000;
    private static final long MAX_CACHE_BYTES = 8L * 1024 * 1024;

    private final File cacheDirectory;

    GitHubCatalogClient(Context context) {
        cacheDirectory = new File(context.getCacheDir(), "github-catalog");
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs();
    }

    RepositorySource resolveSource(RepositorySourceParser.ParsedSource parsed,
                                   String requestedName, String requestedFolder) throws Exception {
        String metadataUrl = API_BASE + "/repos/" + Uri.encode(parsed.owner)
                + "/" + Uri.encode(parsed.repository);
        JSONObject metadata = new JSONObject(requestJson(metadataUrl));
        if (metadata.optBoolean("private", false)) {
            throw new IOException("Private repositories need authentication and are not supported");
        }
        String branch = parsed.branch.isEmpty()
                ? metadata.optString("default_branch", "main") : parsed.branch;
        String folder = join(parsed.path, requestedFolder);
        String name = requestedName == null || requestedName.trim().isEmpty()
                ? metadata.optString("name", parsed.repository) : requestedName.trim();
        RepositorySource source = new RepositorySource(name, parsed.owner, parsed.repository,
                branch, folder, false);
        // A successful directory listing validates both branch and optional starting folder.
        list(source, "", false);
        return source;
    }

    CatalogPage list(RepositorySource source, String relativePath) throws Exception {
        return list(source, relativePath, true);
    }

    CatalogPage listAll(RepositorySource source) throws Exception {
        String url = API_BASE + "/repos/" + Uri.encode(source.owner) + "/"
                + Uri.encode(source.repository) + "/git/trees/" + Uri.encode(source.branch)
                + "?recursive=1";
        CachedJson response = readJson(url, true);
        JSONObject root = new JSONObject(response.json);
        JSONArray tree = root.getJSONArray("tree");
        List<CatalogItem> items = new ArrayList<>();
        boolean capped = false;
        for (int i = 0; i < tree.length(); i++) {
            JSONObject object = tree.getJSONObject(i);
            if (!"blob".equals(object.optString("type"))) continue;
            String absolutePath = RepositorySource.normalizePath(object.optString("path", ""));
            String relativePath = underRoot(source, absolutePath);
            if (relativePath == null || isHiddenPath(relativePath)) continue;
            String name = lastSegment(relativePath);
            if (!WallpaperRules.isSupportedName(name)) continue;
            items.add(new CatalogItem(name, absolutePath, "file",
                    object.optLong("size", -1), object.optString("sha", "")));
            if (items.size() >= MAX_ALL_ITEMS) {
                capped = true;
                break;
            }
        }
        items.sort(itemComparator());
        return new CatalogPage(items, capped || root.optBoolean("truncated", false), response.stale);
    }

    void clearCache() {
        File[] files = cacheDirectory.listFiles();
        if (files == null) return;
        for (File file : files) file.delete();
    }

    private CatalogPage list(RepositorySource source, String relativePath, boolean allowCache)
            throws Exception {
        String absolutePath = source.resolvePath(relativePath);
        StringBuilder url = new StringBuilder(API_BASE)
                .append("/repos/").append(Uri.encode(source.owner))
                .append('/').append(Uri.encode(source.repository)).append("/contents");
        if (!absolutePath.isEmpty()) url.append('/').append(Uri.encode(absolutePath, "/"));
        url.append("?ref=").append(Uri.encode(source.branch));

        CachedJson response = readJson(url.toString(), allowCache);
        Object parsed = new org.json.JSONTokener(response.json).nextValue();
        if (!(parsed instanceof JSONArray)) {
            throw new IOException("The selected starting path is not a directory");
        }
        JSONArray array = (JSONArray) parsed;
        List<CatalogItem> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            String type = object.optString("type", "");
            String name = object.optString("name", "");
            String path = object.optString("path", "");
            if (name.startsWith(".") || isHiddenPath(source.relativePath(path))) continue;
            if ("dir".equals(type)) {
                result.add(new CatalogItem(name, path, type, 0,
                        object.optString("sha", "")));
            } else if ("file".equals(type) && WallpaperRules.isSupportedName(name)) {
                result.add(new CatalogItem(name, path, type,
                        object.optLong("size", -1), object.optString("sha", "")));
            }
        }
        result.sort(itemComparator());
        boolean hasDirectories = false;
        for (CatalogItem item : result) {
            if (item.isDirectory()) {
                hasDirectories = true;
                break;
            }
        }
        if (hasDirectories) {
            try {
                result = CategoryPreviewSelector.attach(result, listAll(source).items);
            } catch (Exception ignored) {
                // Category browsing still works when the optional cover index is unavailable.
            }
        }
        return new CatalogPage(result, false, response.stale);
    }

    private CachedJson readJson(String url, boolean allowCache) throws IOException {
        File cache = new File(cacheDirectory, sha256(url) + ".json");
        long age = cache.isFile() ? System.currentTimeMillis() - cache.lastModified() : Long.MAX_VALUE;
        if (allowCache && age >= 0 && age < CACHE_FRESH_MS) {
            return new CachedJson(readFile(cache), false);
        }
        try {
            String json = requestJson(url);
            writeFile(cache, json);
            pruneCache();
            return new CachedJson(json, false);
        } catch (IOException exception) {
            if (allowCache && cache.isFile()) return new CachedJson(readFile(cache), true);
            throw exception;
        }
    }

    private String requestJson(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(40_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "HorizonDeck/1.2");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String remaining = connection.getHeaderField("X-RateLimit-Remaining");
                String suffix = "0".equals(remaining)
                        ? ". GitHub's anonymous rate limit is exhausted; try again later" : "";
                throw new IOException("GitHub returned HTTP " + status + suffix);
            }
            return readUtf8(connection.getInputStream(), MAX_JSON_BYTES);
        } finally {
            connection.disconnect();
        }
    }

    private static String readUtf8(InputStream input, int maxBytes) throws IOException {
        try (InputStream closeable = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = closeable.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("GitHub response is too large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return readUtf8(input, MAX_JSON_BYTES);
        }
    }

    private static void writeFile(File file, String value) throws IOException {
        File partial = new File(file.getAbsolutePath() + ".part");
        try (FileOutputStream output = new FileOutputStream(partial)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (file.exists() && !file.delete()) throw new IOException("Unable to refresh catalog cache");
        if (!partial.renameTo(file)) throw new IOException("Unable to finish catalog cache");
    }

    private void pruneCache() {
        File[] files = cacheDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_CACHE_BYTES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= MAX_CACHE_BYTES * 3 / 4) break;
            long size = file.length();
            if (file.delete()) total -= size;
        }
    }

    private static Comparator<CatalogItem> itemComparator() {
        return Comparator.comparing((CatalogItem item) -> !item.isDirectory())
                .thenComparing(item -> item.path.toLowerCase(Locale.ROOT));
    }

    private static String underRoot(RepositorySource source, String absolutePath) {
        if (source.rootPath.isEmpty()) return absolutePath;
        if (absolutePath.equals(source.rootPath)) return "";
        String prefix = source.rootPath + "/";
        return absolutePath.startsWith(prefix) ? absolutePath.substring(prefix.length()) : null;
    }

    private static boolean isHiddenPath(String path) {
        if (path == null || path.isEmpty()) return false;
        for (String segment : path.split("/")) {
            if (segment.startsWith(".")) return true;
        }
        return false;
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String join(String first, String second) {
        String left = RepositorySource.normalizePath(first);
        String right = RepositorySource.normalizePath(second);
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + "/" + right;
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

    private static final class CachedJson {
        final String json;
        final boolean stale;

        CachedJson(String json, boolean stale) {
            this.json = json;
            this.stale = stale;
        }
    }
}
