package uk.darkbyte.deckscape;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** File-format, byte-limit, filename, and download-origin validation rules. */
final class WallpaperRules {
    static final long MAX_STATIC_BYTES = 40L * 1024 * 1024;
    static final long MAX_GIF_BYTES = 12L * 1024 * 1024;
    static final long MAX_PREVIEW_FETCH_BYTES = 24L * 1024 * 1024;

    private WallpaperRules() {}

    static boolean isSupportedName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif");
    }

    static boolean isGif(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    static long maxBytesFor(String name) {
        return isGif(name) ? MAX_GIF_BYTES : MAX_STATIC_BYTES;
    }

    static boolean canInstall(CatalogItem item) {
        return !item.isDirectory()
                && isSupportedName(item.name)
                && item.size >= 0
                && item.size <= maxBytesFor(item.name);
    }

    static String safeFileName(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 96) safe = safe.substring(safe.length() - 96);
        return safe.isEmpty() ? "wallpaper" : safe;
    }

    /** Verifies that a raw URL remains HTTPS and belongs to the selected repository. */
    static boolean isAllowedRawUrl(String value, RepositorySource source) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            if (!"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())) return false;
            String[] parts = uri.getRawPath().split("/");
            if (parts.length < 5) return false;
            String owner = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());
            String repository = URLDecoder.decode(parts[2], StandardCharsets.UTF_8.name());
            return source.owner.equalsIgnoreCase(owner)
                    && source.repository.equalsIgnoreCase(repository);
        } catch (Exception ignored) {
            return false;
        }
    }
}
