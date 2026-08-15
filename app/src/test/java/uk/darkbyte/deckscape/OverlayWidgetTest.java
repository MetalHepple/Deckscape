package uk.darkbyte.deckscape;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.EnumSet;

public final class OverlayWidgetTest {
    @Test
    public void withoutVehicleProviderOnlyIndependentCardsAreAvailable() {
        assertEquals(EnumSet.of(OverlayWidget.CLOCK, OverlayWidget.WEATHER),
                OverlayWidget.availableWhen(false));
    }

    @Test
    public void vehicleProviderMakesEveryCardAvailable() {
        assertEquals(EnumSet.allOf(OverlayWidget.class),
                OverlayWidget.availableWhen(true));
    }
}
