package uk.darkbyte.deckscape;

import java.util.Locale;

/** Immutable file or directory returned by a configured GitHub catalog. */
final class CatalogItem {
    final String name;
    final String path;
    final String type;
    final long size;
    final String sha;
    final CatalogItem preview;

    CatalogItem(String name, String path, String type, long size, String sha) {
        this(name, path, type, size, sha, null);
    }

    CatalogItem(String name, String path, String type, long size, String sha,
                CatalogItem preview) {
        this.name = name;
        this.path = RepositorySource.normalizePath(path);
        this.type = type;
        this.size = size;
        this.sha = sha == null ? "" : sha;
        this.preview = preview;
    }

    boolean isDirectory() {
        return "dir".equals(type);
    }

    boolean isGif() {
        return WallpaperRules.isGif(name);
    }

    CatalogItem withPreview(CatalogItem value) {
        return new CatalogItem(name, path, type, size, sha, value);
    }

    String stableKey(RepositorySource source) {
        return source.id() + ":" + (sha.isEmpty() ? path : sha);
    }

    String humanSize() {
        if (size < 0) return "Unknown size";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", size / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
    }
}
