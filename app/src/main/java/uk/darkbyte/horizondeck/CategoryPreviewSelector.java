package uk.darkbyte.horizondeck;

import java.util.ArrayList;
import java.util.List;

final class CategoryPreviewSelector {
    private CategoryPreviewSelector() {}

    static List<CatalogItem> attach(List<CatalogItem> listedItems,
                                    List<CatalogItem> wallpaperFiles) {
        List<CatalogItem> result = new ArrayList<>(listedItems.size());
        for (CatalogItem item : listedItems) {
            if (!item.isDirectory()) {
                result.add(item);
                continue;
            }
            CatalogItem best = null;
            for (CatalogItem candidate : wallpaperFiles) {
                if (!isInside(item.path, candidate.path)) continue;
                if (best == null || compare(item.path, candidate, best) < 0) {
                    best = candidate;
                }
            }
            result.add(best == null ? item : item.withPreview(best));
        }
        return result;
    }

    private static boolean isInside(String directory, String candidate) {
        return candidate.startsWith(directory + "/");
    }

    private static int compare(String directory, CatalogItem left, CatalogItem right) {
        int result = Integer.compare(depthBelow(directory, left.path),
                depthBelow(directory, right.path));
        if (result != 0) return result;
        result = Boolean.compare(left.isGif(), right.isGif());
        if (result != 0) return result;
        result = Boolean.compare(!previewFallbackSafe(left), !previewFallbackSafe(right));
        if (result != 0) return result;
        return left.path.compareToIgnoreCase(right.path);
    }

    private static int depthBelow(String directory, String path) {
        String relative = path.substring(directory.length() + 1);
        int depth = 0;
        for (int i = 0; i < relative.length(); i++) {
            if (relative.charAt(i) == '/') depth++;
        }
        return depth;
    }

    private static boolean previewFallbackSafe(CatalogItem item) {
        long maximum = item.isGif()
                ? WallpaperRules.MAX_GIF_BYTES : WallpaperRules.MAX_PREVIEW_FETCH_BYTES;
        return item.size >= 0 && item.size <= maximum;
    }
}
