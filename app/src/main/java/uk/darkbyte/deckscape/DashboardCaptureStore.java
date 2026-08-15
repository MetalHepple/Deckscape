package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;

/** Owns the private dashboard reference and crash-safe widget restoration state. */
final class DashboardCaptureStore {
    private static final String PREFS = "dashboard_capture";
    private static final String KEY_PENDING = "capture_pending";
    private static final String KEY_RETURN_TO_WIDGETS = "return_to_widgets";
    private static final String KEY_STARTED_AT = "capture_started_at";
    private static final String KEY_ORIGINAL_CLOCK = "original_clock_enabled";
    private static final String KEY_ORIGINAL_WEATHER = "original_weather_enabled";
    private static final String KEY_ORIGINAL_VEHICLE_BATTERY =
            "original_vehicle_battery_enabled";
    private static final String KEY_ORIGINAL_VEHICLE_TEMPERATURES =
            "original_vehicle_temperatures_enabled";
    private static final String KEY_ORIGINAL_VEHICLE_TYRES =
            "original_vehicle_tyres_enabled";
    private static final String KEY_CAPTURED_AT = "captured_at";
    private static final String FILE_NAME = "dashboard-reference.png";
    private static final long INTERRUPTED_CAPTURE_MILLIS = 20_000L;

    private DashboardCaptureStore() {}

    static void begin(Context context, EnumSet<OverlayWidget> enabledWidgets, long nowMillis) {
        EnumSet<OverlayWidget> enabled = enabledWidgets == null
                ? EnumSet.noneOf(OverlayWidget.class) : EnumSet.copyOf(enabledWidgets);
        preferences(context).edit()
                .putBoolean(KEY_PENDING, true)
                .putBoolean(KEY_RETURN_TO_WIDGETS, true)
                .putLong(KEY_STARTED_AT, nowMillis)
                .putBoolean(KEY_ORIGINAL_CLOCK, enabled.contains(OverlayWidget.CLOCK))
                .putBoolean(KEY_ORIGINAL_WEATHER, enabled.contains(OverlayWidget.WEATHER))
                .putBoolean(KEY_ORIGINAL_VEHICLE_BATTERY,
                        enabled.contains(OverlayWidget.VEHICLE_BATTERY))
                .putBoolean(KEY_ORIGINAL_VEHICLE_TEMPERATURES,
                        enabled.contains(OverlayWidget.VEHICLE_TEMPERATURES))
                .putBoolean(KEY_ORIGINAL_VEHICLE_TYRES,
                        enabled.contains(OverlayWidget.VEHICLE_TYRES))
                .apply();
    }

    static boolean isPending(Context context) {
        return preferences(context).getBoolean(KEY_PENDING, false);
    }

    static boolean shouldReturnToWidgets(Context context) {
        return preferences(context).getBoolean(KEY_RETURN_TO_WIDGETS, false);
    }

    static void clearReturnToWidgets(Context context) {
        preferences(context).edit().remove(KEY_RETURN_TO_WIDGETS).apply();
    }

    static long capturedAt(Context context) {
        return preferences(context).getLong(KEY_CAPTURED_AT, 0);
    }

    static boolean recoverInterrupted(Context context, long nowMillis) {
        SharedPreferences value = preferences(context);
        long startedAt = value.getLong(KEY_STARTED_AT, 0);
        if (!value.getBoolean(KEY_PENDING, false)
                || !DashboardCapturePolicy.shouldRecover(nowMillis, startedAt,
                INTERRUPTED_CAPTURE_MILLIS)) return false;
        restoreWidgets(context);
        return true;
    }

    static long recoveryDelayMillis(Context context, long nowMillis) {
        SharedPreferences value = preferences(context);
        if (!value.getBoolean(KEY_PENDING, false)) return -1;
        return DashboardCapturePolicy.recoveryDelayMillis(nowMillis,
                value.getLong(KEY_STARTED_AT, 0), INTERRUPTED_CAPTURE_MILLIS);
    }

    static void disableWidgets(Context context) {
        new OverlaySettings(context).setEnabledWidgets(EnumSet.noneOf(OverlayWidget.class));
        broadcastOverlayChange(context);
    }

    static void restoreWidgets(Context context) {
        SharedPreferences value = preferences(context);
        if (!value.getBoolean(KEY_PENDING, false)) return;
        EnumSet<OverlayWidget> enabled = EnumSet.noneOf(OverlayWidget.class);
        restoreMembership(value, enabled, OverlayWidget.CLOCK, KEY_ORIGINAL_CLOCK);
        restoreMembership(value, enabled, OverlayWidget.WEATHER, KEY_ORIGINAL_WEATHER);
        restoreMembership(value, enabled, OverlayWidget.VEHICLE_BATTERY,
                KEY_ORIGINAL_VEHICLE_BATTERY);
        restoreMembership(value, enabled, OverlayWidget.VEHICLE_TEMPERATURES,
                KEY_ORIGINAL_VEHICLE_TEMPERATURES);
        restoreMembership(value, enabled, OverlayWidget.VEHICLE_TYRES,
                KEY_ORIGINAL_VEHICLE_TYRES);
        new OverlaySettings(context).setEnabledWidgets(enabled);
        value.edit().putBoolean(KEY_PENDING, false).apply();
        broadcastOverlayChange(context);
    }

    static File referenceFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    static boolean hasReference(Context context) {
        return referenceFile(context).isFile();
    }

    static Bitmap decodeReference(Context context) {
        File file = referenceFile(context);
        if (!file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (!DashboardCapturePolicy.validDimensions(bounds.outWidth, bounds.outHeight)) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    static void saveReference(Context context, Bitmap bitmap, long capturedAtMillis)
            throws IOException {
        if (bitmap == null || !DashboardCapturePolicy.validDimensions(
                bitmap.getWidth(), bitmap.getHeight())) {
            throw new IOException("Dashboard capture dimensions are invalid");
        }
        AtomicFile atomicFile = new AtomicFile(referenceFile(context));
        FileOutputStream output = null;
        try {
            output = atomicFile.startWrite();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Dashboard capture could not be encoded");
            }
            atomicFile.finishWrite(output);
            output = null;
            preferences(context).edit()
                    .putLong(KEY_CAPTURED_AT, capturedAtMillis)
                    .apply();
        } finally {
            if (output != null) atomicFile.failWrite(output);
        }
    }

    static void deleteReference(Context context) {
        AtomicFile atomicFile = new AtomicFile(referenceFile(context));
        atomicFile.delete();
        preferences(context).edit().remove(KEY_CAPTURED_AT).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void restoreMembership(SharedPreferences preferences,
                                          EnumSet<OverlayWidget> enabled,
                                          OverlayWidget widget, String key) {
        if (preferences.getBoolean(key, false)) enabled.add(widget);
    }

    private static void broadcastOverlayChange(Context context) {
        context.sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                .setPackage(context.getPackageName()));
    }
}
