package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class OverlayPositionTest {
    @Test
    public void parseAcceptsStoredValues() {
        assertEquals(OverlayPosition.TOP_RIGHT, OverlayPosition.parse("TOP_RIGHT"));
        assertEquals(OverlayPosition.BOTTOM_LEFT, OverlayPosition.parse("BOTTOM_LEFT"));
    }

    @Test
    public void parseFallsBackForMissingOrInvalidValues() {
        assertEquals(OverlayPosition.TOP_LEFT, OverlayPosition.parse(null));
        assertEquals(OverlayPosition.TOP_LEFT, OverlayPosition.parse("CENTER"));
    }
}
