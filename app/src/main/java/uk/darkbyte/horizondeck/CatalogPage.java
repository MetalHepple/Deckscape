package uk.darkbyte.horizondeck;

import java.util.List;

final class CatalogPage {
    final List<CatalogItem> items;
    final boolean truncated;
    final boolean staleCache;

    CatalogPage(List<CatalogItem> items, boolean truncated, boolean staleCache) {
        this.items = items;
        this.truncated = truncated;
        this.staleCache = staleCache;
    }
}
