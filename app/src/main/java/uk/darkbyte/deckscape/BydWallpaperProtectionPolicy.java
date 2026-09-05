package uk.darkbyte.deckscape;

import android.os.Build;

/** Limits the same-device ADB repair to the verified BYD head-unit family. */
final class BydWallpaperProtectionPolicy {
    static final String PACKAGE_NAME = "com.byd.wallpaperhome";
    static final String FORCE_STOP_COMMAND =
            "am force-stop --user 0 " + PACKAGE_NAME;
    static final String STOP_IF_RUNNING_COMMAND =
            "if pidof " + PACKAGE_NAME + " >/dev/null; then "
                    + "cmd activity force-stop --user 0 " + PACKAGE_NAME + "; fi";
    static final String VERIFY_STOPPED_COMMAND =
            "dumpsys package " + PACKAGE_NAME + " | grep 'User 0:'";

    private BydWallpaperProtectionPolicy() {}

    static boolean isAvailable() {
        return supportsHardware(Build.MODEL, Build.DEVICE, Build.PRODUCT);
    }

    static boolean supportsHardware(String model, String device, String product) {
        return matches(model, "BYD AUTO")
                && (matches(device, "DiLink3.0") || matches(product, "DiLink3.0"));
    }

    static boolean repairSucceeded(int stopExitCode, int verifyExitCode,
                                   String verificationOutput) {
        return stopExitCode == 0
                && verifyExitCode == 0
                && verificationOutput != null
                && verificationOutput.contains("stopped=true");
    }

    private static boolean matches(String actual, String expected) {
        return actual != null && expected.equalsIgnoreCase(actual.trim());
    }
}
