package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Verifies safe profile migration and malformed-value handling. */
public class WallpaperProfileTest {
    @Test
    public void missingProfileKeepsLegacyFillAndBothDefaults() {
        WallpaperProfile profile = WallpaperProfile.fromJson(null);
        assertEquals(ScaleMode.DEFAULT, profile.scaleMode);
        assertEquals(DayNightRole.BOTH, profile.role);
        assertEquals(1f, profile.zoom, 0.001f);
    }

    @Test
    public void serializedProfileRoundTrips() throws Exception {
        WallpaperProfile original = new WallpaperProfile(
                ScaleMode.CUSTOM, 1.7f, 0.2f, 0.8f, DayNightRole.NIGHT);
        WallpaperProfile parsed = WallpaperProfile.fromJson(original.toJson().toString());
        assertEquals(ScaleMode.CUSTOM, parsed.scaleMode);
        assertEquals(DayNightRole.NIGHT, parsed.role);
        assertEquals(1.7f, parsed.zoom, 0.001f);
        assertEquals(0.2f, parsed.focusX, 0.001f);
        assertEquals(0.8f, parsed.focusY, 0.001f);
    }

    @Test
    public void malformedValuesAreBounded() {
        WallpaperProfile parsed = WallpaperProfile.fromJson(
                "{\"scale\":\"BAD\",\"zoom\":99,\"focusX\":-2,\"focusY\":4}");
        assertEquals(ScaleMode.DEFAULT, parsed.scaleMode);
        assertEquals(3f, parsed.zoom, 0.001f);
        assertEquals(0f, parsed.focusX, 0.001f);
        assertEquals(1f, parsed.focusY, 0.001f);
    }
}
