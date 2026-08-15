package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

/** Runtime branding and package detection for the separately installed Overdrive app. */
final class OverdriveBrand {
    static final String PACKAGE_NAME = "com.overdrive.app";

    private OverdriveBrand() {
    }

    static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    static Drawable loadInstalledIcon(Context context) {
        try {
            return context.getPackageManager().getApplicationIcon(PACKAGE_NAME).mutate();
        } catch (PackageManager.NameNotFoundException exception) {
            return null;
        }
    }
}
