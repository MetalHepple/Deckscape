package uk.darkbyte.deckscape;

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
    private static final long CONTRIBUTORS_FRESH_MS = 24L * 60 * 60 * 1000;
    private static final long LICENSE_FRESH_MS = 7L * 24 * 60 * 60 * 1000;

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
        List<RepositoryMetadata.Contributor> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            String login = item.optString("login", item.optString("name", "")).trim();
            String type = item.optString("type", "");
            if (login.isEmpty() || "Bot".equalsIgnoreCase(type)
                    || login.toLowerCase(Locale.ROOT).endsWith("[bot]")) continue;
            String page = item.optString("html_url", "");
            if (!isAllowedGitHubPage(page)) page = "https://github.com/" + login;
            result.add(new RepositoryMetadata.Contributor(login, page,
                    Math.max(0, item.optInt("contributions", 0))));
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
