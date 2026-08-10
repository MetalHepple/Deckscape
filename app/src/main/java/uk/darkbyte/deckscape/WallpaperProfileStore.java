package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Persists per-wallpaper metadata without changing the downloaded library filenames. */
final class WallpaperProfileStore {
    private static final String PREFS = "wallpaper_profiles";

    private final SharedPreferences preferences;

    WallpaperProfileStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    WallpaperProfile get(File file) {
        return file == null ? WallpaperProfile.DEFAULT
                : WallpaperProfile.fromJson(preferences.getString(file.getName(), null));
    }

    void put(File file, WallpaperProfile profile) {
        if (file == null || profile == null) return;
        try {
            preferences.edit().putString(file.getName(), profile.toJson().toString()).apply();
        } catch (Exception ignored) {
            // The fixed primitive profile fields should always serialize successfully.
        }
    }

    void remove(File file) {
        if (file != null) preferences.edit().remove(file.getName()).apply();
    }
}
