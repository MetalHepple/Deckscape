package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies safe profile migration and malformed-value handling. */
public class WallpaperProfileTest {
    @Test
    public void missingProfileKeepsLegacyFillAndBothDefaults() {
        WallpaperProfile profile = WallpaperProfile.fromJson(null);
        assertEquals(ScaleMode.DEFAULT, profile.scaleMode);
        assertEquals(DayNightRole.BOTH, profile.role);
        assertEquals(DayNightRole.BOTH, profile.automaticRole);
        assertTrue(Double.isNaN(profile.automaticLuminance));
        assertEquals(1f, profile.zoom, 0.001f);
    }

    @Test
    public void serializedProfileRoundTrips() throws Exception {
        WallpaperProfile original = new WallpaperProfile(
                ScaleMode.CUSTOM, 1.7f, 0.2f, 0.8f, DayNightRole.NIGHT)
                .withAutomaticAssignment(0.18, DayNightRole.NIGHT);
        WallpaperProfile parsed = WallpaperProfile.fromJson(original.toJson().toString());
        assertEquals(ScaleMode.CUSTOM, parsed.scaleMode);
        assertEquals(DayNightRole.NIGHT, parsed.role);
        assertEquals(DayNightRole.NIGHT, parsed.automaticRole);
        assertEquals(0.18, parsed.automaticLuminance, 0.001);
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
