package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class OverlayPlacementTest {
    @Test
    public void clampsCoordinatesToTheDashboard() {
        OverlayPlacement placement = new OverlayPlacement(-2f, 4f);
        assertEquals(0f, placement.x, 0f);
        assertEquals(1f, placement.y, 0f);
    }

    @Test
    public void replacesNonFiniteCoordinatesWithCentre() {
        OverlayPlacement placement = new OverlayPlacement(Float.NaN,
                Float.POSITIVE_INFINITY);
        assertEquals(0.5f, placement.x, 0f);
        assertEquals(0.5f, placement.y, 0f);
    }

    @Test
    public void migratesLegacyBottomRightPanelAsTwoIndependentCards() {
        OverlayPlacement clock = OverlayPlacement.legacyClock(OverlayPosition.BOTTOM_RIGHT);
        OverlayPlacement weather = OverlayPlacement.legacyWeather(OverlayPosition.BOTTOM_RIGHT);
        assertEquals(0.679f, clock.x, 0.001f);
        assertEquals(0.876f, weather.x, 0.001f);
        assertEquals(0.903f, clock.y, 0.001f);
        assertEquals(0.903f, weather.y, 0.001f);
    }
}
