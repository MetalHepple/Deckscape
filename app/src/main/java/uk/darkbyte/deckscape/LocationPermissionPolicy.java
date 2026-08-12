package uk.darkbyte.deckscape;

/** Chooses the least-privileged foreground location grant that is reliable per Android release. */
final class LocationPermissionPolicy {
    private static final int ANDROID_12_API = 31;

    private LocationPermissionPolicy() {}

    /** Older releases need fine access for reliable GPS on head units without network location. */
    static boolean isSufficient(int sdk, boolean fineGranted, boolean coarseGranted) {
        return fineGranted || (sdk >= ANDROID_12_API && coarseGranted);
    }
}
