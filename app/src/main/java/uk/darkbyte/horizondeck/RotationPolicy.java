package uk.darkbyte.horizondeck;

final class RotationPolicy {
    private RotationPolicy() {}

    static boolean shouldRotate(long nowMillis, long lastSwitchMillis,
                                long intervalMillis, int itemCount) {
        return itemCount > 1
                && intervalMillis > 0
                && lastSwitchMillis > 0
                && nowMillis - lastSwitchMillis >= intervalMillis;
    }

    static int nextIndex(int currentIndex, int itemCount) {
        if (itemCount <= 0) return 0;
        return Math.floorMod(currentIndex + 1, itemCount);
    }
}
