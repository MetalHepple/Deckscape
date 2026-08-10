package uk.darkbyte.deckscape;

import org.json.JSONException;
import org.json.JSONObject;

/** Immutable per-wallpaper presentation and day/night assignment. */
final class WallpaperProfile {
    static final float MIN_ZOOM = 1f;
    static final float MAX_ZOOM = 3f;
    static final WallpaperProfile DEFAULT = new WallpaperProfile(
            ScaleMode.DEFAULT, 1f, 0.5f, 0.5f, DayNightRole.BOTH);

    final ScaleMode scaleMode;
    final float zoom;
    final float focusX;
    final float focusY;
    final DayNightRole role;

    WallpaperProfile(ScaleMode scaleMode, float zoom, float focusX, float focusY,
                     DayNightRole role) {
        this.scaleMode = scaleMode == null ? ScaleMode.DEFAULT : scaleMode;
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        this.focusX = clamp(focusX, 0f, 1f);
        this.focusY = clamp(focusY, 0f, 1f);
        this.role = role == null ? DayNightRole.BOTH : role;
    }

    WallpaperProfile withScaleMode(ScaleMode value) {
        return new WallpaperProfile(value, zoom, focusX, focusY, role);
    }

    WallpaperProfile withCrop(float newZoom, float newFocusX, float newFocusY) {
        return new WallpaperProfile(scaleMode, newZoom, newFocusX, newFocusY, role);
    }

    WallpaperProfile withRole(DayNightRole value) {
        return new WallpaperProfile(scaleMode, zoom, focusX, focusY, value);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("scale", scaleMode.name())
                .put("zoom", zoom)
                .put("focusX", focusX)
                .put("focusY", focusY)
                .put("role", role.name());
    }

    static WallpaperProfile fromJson(String value) {
        if (value == null || value.trim().isEmpty()) return DEFAULT;
        try {
            JSONObject object = new JSONObject(value);
            return new WallpaperProfile(
                    ScaleMode.parse(object.optString("scale", null), ScaleMode.DEFAULT),
                    (float) object.optDouble("zoom", 1),
                    (float) object.optDouble("focusX", 0.5),
                    (float) object.optDouble("focusY", 0.5),
                    DayNightRole.parse(object.optString("role", null)));
        } catch (Exception exception) {
            return DEFAULT;
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
