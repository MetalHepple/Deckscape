package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Persists custom repository sources and merges them with the curated defaults. */
final class SourceStore {
    private static final String PREFS = "repository_sources";
    private static final String KEY_CUSTOM = "custom_sources";

    private final SharedPreferences preferences;

    SourceStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<RepositorySource> list() {
        List<RepositorySource> sources = defaultSources();
        String saved = preferences.getString(KEY_CUSTOM, "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                try {
                    RepositorySource source = RepositorySource.fromJson(array.getJSONObject(i));
                    if (!sources.contains(source)) sources.add(source);
                } catch (Exception ignored) {
                    // Skip only the malformed entry so later valid sources remain available.
                }
            }
        } catch (Exception ignored) {
            // A malformed preference never removes the built-in sources.
        }
        return sources;
    }

    void add(RepositorySource source) {
        List<RepositorySource> custom = customSources();
        custom.remove(source);
        custom.add(source);
        save(custom);
    }

    void remove(RepositorySource source) {
        if (source.builtIn) return;
        List<RepositorySource> custom = customSources();
        custom.remove(source);
        save(custom);
    }

    private List<RepositorySource> customSources() {
        List<RepositorySource> custom = new ArrayList<>();
        for (RepositorySource source : list()) {
            if (!source.builtIn) custom.add(source);
        }
        return custom;
    }

    private void save(List<RepositorySource> custom) {
        JSONArray array = new JSONArray();
        for (RepositorySource source : custom) {
            try {
                array.put(source.toJson());
            } catch (Exception ignored) {
                // The constructor has already validated every source.
            }
        }
        preferences.edit().putString(KEY_CUSTOM, array.toString()).apply();
    }

    /** Returns a new mutable list containing the curated public catalogs. */
    static List<RepositorySource> defaultSources() {
        List<RepositorySource> sources = new ArrayList<>();
        sources.add(new RepositorySource("Wallz", "fr0st-xyz", "wallz", "main", "", true));
        sources.add(new RepositorySource("elementary", "elementary", "wallpapers", "main",
                "backgrounds", true));
        sources.add(new RepositorySource("KDE Breeze", "KDE", "breeze", "master",
                "wallpapers/Next/contents", true));
        sources.add(new RepositorySource("Vyrx", "vyrx-dev", "Wallpapers",
                "master", "", true));
        sources.add(new RepositorySource("Aesthetic", "D3Ext", "aesthetic-wallpapers",
                "main", "images", true));
        sources.add(new RepositorySource("Wall-E-Desk", "JoshuaThadi", "Wall-E-Desk",
                "main", "", true));
        sources.add(new RepositorySource("ItsTerm1n4l", "ItsTerm1n4l", "Wallpapers",
                "main", "images", true));
        return sources;
    }
}
