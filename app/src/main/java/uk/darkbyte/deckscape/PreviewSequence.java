package uk.darkbyte.deckscape;

import java.util.ArrayList;
import java.util.List;

/** Immutable preview-list snapshot with bounded previous and next navigation. */
final class PreviewSequence {
    private final List<CatalogItem> wallpapers = new ArrayList<>();
    private int index;

    PreviewSequence(List<CatalogItem> visibleItems, CatalogItem selected) {
        if (visibleItems != null) {
            for (CatalogItem item : visibleItems) {
                if (item != null && !item.isDirectory()) wallpapers.add(item);
            }
        }
        index = find(selected);
        if (index < 0 && selected != null && !selected.isDirectory()) {
            wallpapers.add(0, selected);
            index = 0;
        }
        if (index < 0) index = 0;
    }

    boolean isEmpty() {
        return wallpapers.isEmpty();
    }

    int size() {
        return wallpapers.size();
    }

    /** Returns the one-based position presented to the user. */
    int position() {
        return isEmpty() ? 0 : index + 1;
    }

    CatalogItem current() {
        return isEmpty() ? null : wallpapers.get(index);
    }

    boolean hasPrevious() {
        return index > 0 && !isEmpty();
    }

    boolean hasNext() {
        return !isEmpty() && index + 1 < wallpapers.size();
    }

    CatalogItem previous() {
        if (hasPrevious()) index--;
        return current();
    }

    CatalogItem next() {
        if (hasNext()) index++;
        return current();
    }

    private int find(CatalogItem selected) {
        if (selected == null) return -1;
        for (int position = 0; position < wallpapers.size(); position++) {
            if (wallpapers.get(position).path.equals(selected.path)) return position;
        }
        return -1;
    }
}
