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
    final double automaticLuminance;
    final DayNightRole automaticRole;

    WallpaperProfile(ScaleMode scaleMode, float zoom, float focusX, float focusY,
                     DayNightRole role) {
        this(scaleMode, zoom, focusX, focusY, role, Double.NaN, DayNightRole.BOTH);
    }

    private WallpaperProfile(ScaleMode scaleMode, float zoom, float focusX, float focusY,
                             DayNightRole role, double automaticLuminance,
                             DayNightRole automaticRole) {
        this.scaleMode = scaleMode == null ? ScaleMode.DEFAULT : scaleMode;
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        this.focusX = clamp(focusX, 0f, 1f);
        this.focusY = clamp(focusY, 0f, 1f);
        this.role = role == null ? DayNightRole.BOTH : role;
        this.automaticLuminance = Double.isFinite(automaticLuminance)
                ? Math.max(0, Math.min(1, automaticLuminance)) : Double.NaN;
        this.automaticRole = automaticRole == null ? DayNightRole.BOTH : automaticRole;
    }

    WallpaperProfile withScaleMode(ScaleMode value) {
        return new WallpaperProfile(value, zoom, focusX, focusY, role,
                automaticLuminance, automaticRole);
    }

    WallpaperProfile withCrop(float newZoom, float newFocusX, float newFocusY) {
        return new WallpaperProfile(scaleMode, newZoom, newFocusX, newFocusY, role,
                automaticLuminance, automaticRole);
    }

    WallpaperProfile withRole(DayNightRole value) {
        return new WallpaperProfile(scaleMode, zoom, focusX, focusY, value,
                automaticLuminance, automaticRole);
    }

    WallpaperProfile withAutomaticAssignment(double luminance, DayNightRole value) {
        return new WallpaperProfile(scaleMode, zoom, focusX, focusY, role,
                luminance, value);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject()
                .put("scale", scaleMode.name())
                .put("zoom", zoom)
                .put("focusX", focusX)
                .put("focusY", focusY)
                .put("role", role.name());
        if (Double.isFinite(automaticLuminance)) {
            object.put("automaticLuminance", automaticLuminance)
                    .put("automaticRole", automaticRole.name());
        }
        return object;
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
                    DayNightRole.parse(object.optString("role", null)),
                    object.optDouble("automaticLuminance", Double.NaN),
                    DayNightRole.parse(object.optString("automaticRole", null)));
        } catch (Exception exception) {
            return DEFAULT;
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
