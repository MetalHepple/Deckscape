package uk.darkbyte.horizondeck;

import java.util.Locale;

final class CatalogItem {
    final String name;
    final String path;
    final String type;
    final long size;
    final String sha;

    CatalogItem(String name, String path, String type, long size, String sha) {
        this.name = name;
        this.path = RepositorySource.normalizePath(path);
        this.type = type;
        this.size = size;
        this.sha = sha == null ? "" : sha;
    }

    boolean isDirectory() {
        return "dir".equals(type);
    }

    boolean isGif() {
        return WallpaperRules.isGif(name);
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
