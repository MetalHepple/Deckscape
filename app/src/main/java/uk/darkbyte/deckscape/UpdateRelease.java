package uk.darkbyte.deckscape;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** Immutable, validated metadata for one stable Deckscape GitHub release. */
final class UpdateRelease {
    /** Immutable GitHub release asset selected from the trusted repository. */
    static final class Asset {
        final String name;
        final String url;
        final long size;

        Asset(String name, String url, long size) {
            this.name = name;
            this.url = url;
            this.size = size;
        }
    }

    final String versionName;
    final String title;
    final String notes;
    final String pageUrl;
    final Asset apk;
    final Asset checksum;
    final String sha256;

    private UpdateRelease(String versionName, String title, String notes, String pageUrl,
                          Asset apk, Asset checksum, String sha256) {
        this.versionName = versionName;
        this.title = title;
        this.notes = notes;
        this.pageUrl = pageUrl;
        this.apk = apk;
        this.checksum = checksum;
        this.sha256 = sha256;
    }

    /** Parses the response from GitHub's latest-release endpoint. */
    static UpdateRelease parse(String json) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) {
                throw new IOException("GitHub returned a non-stable release");
            }
            String tag = root.optString("tag_name", "");
            String version = UpdateVersion.normalize(tag);
            String pageUrl = root.optString("html_url", "");
            if (!UpdateRules.isAllowedReleasePage(pageUrl)) {
                throw new IOException("GitHub returned an unexpected release page");
            }

            JSONArray values = root.optJSONArray("assets");
            if (values == null) throw new IOException("The release has no downloadable assets");
            JSONObject exactApk = null;
            String expectedName = "Deckscape-" + version + ".apk";
            for (int index = 0; index < values.length(); index++) {
                JSONObject asset = values.getJSONObject(index);
                String name = asset.optString("name", "");
                if (expectedName.equals(name)) exactApk = asset;
            }
            if (exactApk == null) throw new IOException("The release APK has an unexpected name");
            JSONObject apkObject = exactApk;
            Asset apk = asset(apkObject, true);

            String digest = UpdateRules.sha256(apkObject.optString("digest", null));
            Asset checksum = null;
            for (int index = 0; index < values.length(); index++) {
                JSONObject asset = values.getJSONObject(index);
                String name = asset.optString("name", "");
                if ((apk.name + ".sha256").equals(name)
                        || ("Deckscape-" + version + ".apk.sha256").equals(name)) {
                    checksum = asset(asset, false);
                    break;
                }
            }
            if (digest == null && checksum == null) {
                throw new IOException("The release does not provide a SHA-256 checksum");
            }
            String title = root.optString("name", tag).trim();
            if (title.isEmpty()) title = "Deckscape " + version;
            String notes = root.optString("body", "").trim();
            if (notes.length() > 8_000) notes = notes.substring(0, 8_000) + "…";
            return new UpdateRelease(version, title, notes, pageUrl,
                    apk, checksum, digest);
        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException("GitHub returned invalid release metadata", exception);
        }
    }

    static UpdateRelease restored(String versionName, String title, String notes, String pageUrl) {
        return new UpdateRelease(versionName, title, notes, pageUrl,
                null, null, null);
    }

    private static Asset asset(JSONObject value, boolean apk) throws IOException {
        String name = value.optString("name", "");
        String url = value.optString("browser_download_url", "");
        long size = value.optLong("size", -1L);
        if (name.isEmpty() || !UpdateRules.isAllowedReleaseUrl(url) || size <= 0) {
            throw new IOException("GitHub returned an invalid release asset");
        }
        long maximum = apk ? UpdateRules.MAX_APK_BYTES : UpdateRules.MAX_CHECKSUM_BYTES;
        if (size > maximum) throw new IOException("The release asset exceeds the safe size limit");
        return new Asset(name, url, size);
    }
}
