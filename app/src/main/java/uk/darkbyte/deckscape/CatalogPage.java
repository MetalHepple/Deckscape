package uk.darkbyte.deckscape;

import java.util.List;

/** Catalog result with cache and upstream-truncation metadata for the status UI. */
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
