package uk.darkbyte.deckscape;

/** Pure scheduling rules shared by the live-wallpaper engine and unit tests. */
final class RotationPolicy {
    private RotationPolicy() {}

    static boolean isSlideshowEnabled(long intervalMillis) {
        return intervalMillis > 0;
    }

    static boolean shouldRotate(long nowMillis, long lastSwitchMillis,
                                long intervalMillis, int itemCount) {
        return itemCount > 1
                && isSlideshowEnabled(intervalMillis)
                && lastSwitchMillis > 0
                && nowMillis - lastSwitchMillis >= intervalMillis;
    }

    static int nextIndex(int currentIndex, int itemCount) {
        if (itemCount <= 0) return 0;
        return Math.floorMod(currentIndex + 1, itemCount);
    }
}
