package uk.darkbyte.deckscape;

/** Identifies only the provider notification added to the verified BYD Android 10 engine. */
final class BydWallpaperVisibilityPolicy {
    private static final String VENDOR_ENGINE =
            "android.service.wallpaper.WallpaperService$Engine";
    private static final String VENDOR_NOTIFICATION = "notifyWallpaperVisibility";

    private BydWallpaperVisibilityPolicy() {}

    static boolean supportsWorkaround(boolean hardwareEligible, int apiLevel) {
        return hardwareEligible && apiLevel == 29;
    }

    static boolean isVendorNotification(StackTraceElement[] resolverCallStack) {
        // The stack is captured inside getContentResolver: frame 1 is its direct
        // caller. A match deeper in the stack must not block unrelated resolver work.
        if (resolverCallStack == null || resolverCallStack.length < 2) return false;
        StackTraceElement caller = resolverCallStack[1];
        return caller != null && VENDOR_ENGINE.equals(caller.getClassName())
                && VENDOR_NOTIFICATION.equals(caller.getMethodName());
    }
}
