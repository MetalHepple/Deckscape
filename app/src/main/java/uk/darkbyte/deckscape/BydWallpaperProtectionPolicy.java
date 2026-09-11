package uk.darkbyte.deckscape;

import android.os.Build;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Limits the same-device ADB repair to the verified BYD head-unit family. */
final class BydWallpaperProtectionPolicy {
    static final String PACKAGE_NAME = "com.byd.wallpaperhome";
    static final String THEMES_PACKAGE_NAME = "com.byd.automultipletheme";
    static final String VERIFY_COMMAND = "cmd appops get --user 0 " + PACKAGE_NAME + " WRITE_WALLPAPER";
    static final String PERSIST_COMMAND = "cmd appops write-settings";
    static final String PACKAGE_STATE_COMMAND =
            "dumpsys package " + PACKAGE_NAME + " | grep 'User 0:'";
    static final String IDENTITY_COMMAND = "getprop ro.product.model; getprop ro.product.device; "
            + "getprop ro.product.name; getprop ro.build.version.sdk; am get-current-user";
    private static final Pattern USER_STATE = Pattern.compile(
            "(?m)^\\s*User 0: (?=[^\\r\\n]*\\binstalled=true(?:\\s|$))"
                    + "[^\\r\\n]*\\benabled=([0-4])(?:\\s|$)");
    private static final Pattern OP_STATE = Pattern.compile(
            "WRITE_WALLPAPER: (allow|ignore|deny|default|foreground)(?:;[^\\r\\n]*)?");
    private static final String[] MODES = {"allow", "ignore", "deny", "default", "foreground"};

    private BydWallpaperProtectionPolicy() {}

    static boolean isAvailable() {
        return Build.VERSION.SDK_INT == 29
                && supportsHardware(Build.MODEL, Build.DEVICE, Build.PRODUCT);
    }

    static boolean supportsHardware(String model, String device, String product) {
        return matches(model, "BYD AUTO")
                && (matches(device, "DiLink3.0") || matches(product, "DiLink3.0"));
    }

    static boolean identityMatches(String output) {
        if (output == null) return false;
        String[] lines = output.trim().split("\\r?\\n", -1);
        return lines.length == 5 && supportsHardware(lines[0], lines[1], lines[2])
                && "29".equals(lines[3].trim()) && "0".equals(lines[4].trim());
    }

    static int packageState(int exitCode, String output) {
        if (exitCode != 0 || output == null || output.length() > 8192) return -1;
        Matcher matcher = USER_STATE.matcher(output);
        if (!matcher.find()) return -1;
        int state = Integer.parseInt(matcher.group(1));
        return matcher.find() ? -1 : state;
    }

    static int operationState(int exitCode, String output) {
        if (exitCode != 0 || output == null || output.length() > 8192) return -1;
        String value = output.trim().replace("\r\n", "\n");
        if ("No operations.\nDefault mode: allow".equals(value)) return 0;
        // UID-level overrides are intentionally rejected: never change shared system UID 1000.
        Matcher matcher = OP_STATE.matcher(value);
        if (!matcher.matches()) return -1;
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(matcher.group(1))) return i;
        }
        return -1;
    }

    static String operationCommand(int mode) {
        if (mode < 0 || mode >= MODES.length) throw new IllegalArgumentException("Unsupported AppOp mode");
        return "cmd appops set --user 0 " + PACKAGE_NAME + " WRITE_WALLPAPER " + MODES[mode];
    }

    static String legacyRestoreCommand(int previousState) {
        if (previousState == 0) return "pm default-state --user 0 " + PACKAGE_NAME;
        if (previousState == 1) return "pm enable --user 0 " + PACKAGE_NAME;
        throw new IllegalArgumentException("Unsupported original package state");
    }

    private static boolean matches(String actual, String expected) {
        return actual != null && expected.equalsIgnoreCase(actual.trim());
    }
}
