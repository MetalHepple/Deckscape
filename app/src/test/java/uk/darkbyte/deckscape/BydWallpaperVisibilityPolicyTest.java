package uk.darkbyte.deckscape;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BydWallpaperVisibilityPolicyTest {
    private static final StackTraceElement RESOLVER = frame(
            "uk.darkbyte.deckscape.WallpaperEngineService", "getContentResolver");
    private static final StackTraceElement VENDOR = frame(
            "android.service.wallpaper.WallpaperService$Engine", "notifyWallpaperVisibility");

    @Test
    public void restrictedToVerifiedHardwareAndAndroid10() {
        assertTrue(BydWallpaperVisibilityPolicy.supportsWorkaround(true, 29));
        assertFalse(BydWallpaperVisibilityPolicy.supportsWorkaround(false, 29));
        assertFalse(BydWallpaperVisibilityPolicy.supportsWorkaround(true, 28));
        assertFalse(BydWallpaperVisibilityPolicy.supportsWorkaround(true, 30));
        assertFalse(BydWallpaperVisibilityPolicy.supportsWorkaround(true, 36));
    }

    @Test
    public void recognizesTheDirectVendorCallbackWithoutLineNumberDependency() {
        assertTrue(BydWallpaperVisibilityPolicy.isVendorNotification(
                new StackTraceElement[] {RESOLVER, VENDOR}));
        assertTrue(BydWallpaperVisibilityPolicy.isVendorNotification(
                new StackTraceElement[] {RESOLVER, new StackTraceElement(
                        VENDOR.getClassName(), VENDOR.getMethodName(), null, -1)}));
    }

    @Test
    public void ordinaryFrameworkAndAppResolverCallsPassThrough() {
        assertFalse(BydWallpaperVisibilityPolicy.isVendorNotification(
                new StackTraceElement[] {RESOLVER, frame(
                        "android.service.wallpaper.WallpaperService$Engine", "updateSurface")}));
        assertFalse(BydWallpaperVisibilityPolicy.isVendorNotification(
                new StackTraceElement[] {RESOLVER, frame(
                        "uk.darkbyte.deckscape.MainActivity", "notifyWallpaperVisibility")}));
        assertFalse(BydWallpaperVisibilityPolicy.isVendorNotification(
                new StackTraceElement[] {RESOLVER, frame(
                        "android.provider.Settings$System", "getInt"), VENDOR}));
    }

    @Test
    public void missingOrUnexpectedStacksPassThrough() {
        assertFalse(BydWallpaperVisibilityPolicy.isVendorNotification(null));
        assertFalse(BydWallpaperVisibilityPolicy.isVendorNotification(new StackTraceElement[0]));
        assertFalse(BydWallpaperVisibilityPolicy.isVendorNotification(
                new StackTraceElement[] {RESOLVER}));
        assertFalse(BydWallpaperVisibilityPolicy.isVendorNotification(
                new StackTraceElement[] {RESOLVER, null}));
    }

    private static StackTraceElement frame(String className, String methodName) {
        return new StackTraceElement(className, methodName, "Test.java", 1);
    }
}
