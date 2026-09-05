package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;

/** Owns lightweight wallpaper presentation preferences shared by the app and engine. */
final class DisplaySettings {
    private static final String KEY_ADAPTIVE_WIDGET_STYLE = "adaptive_widget_style";
    private static final String KEY_CROSSFADE = "wallpaper_crossfade";

    private final SharedPreferences preferences;

    DisplaySettings(Context context) {
        preferences = context.getSharedPreferences(WallpaperEngineService.PREFS,
                Context.MODE_PRIVATE);
    }

    boolean isAdaptiveWidgetStyleEnabled() {
        return preferences.getBoolean(KEY_ADAPTIVE_WIDGET_STYLE, true);
    }

    void setAdaptiveWidgetStyleEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ADAPTIVE_WIDGET_STYLE, enabled).apply();
    }

    boolean isCrossfadeEnabled() {
        return preferences.getBoolean(KEY_CROSSFADE, true);
    }

    void setCrossfadeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_CROSSFADE, enabled).apply();
    }
}
