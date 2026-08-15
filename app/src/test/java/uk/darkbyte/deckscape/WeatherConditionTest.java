package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class WeatherConditionTest {
    @Test
    public void mapsRepresentativeWmoCodes() {
        assertEquals("Clear", WeatherCondition.description(0));
        assertEquals(WeatherCondition.Icon.PARTLY_CLOUDY, WeatherCondition.icon(2));
        assertEquals("Fog", WeatherCondition.description(48));
        assertEquals(WeatherCondition.Icon.RAIN, WeatherCondition.icon(65));
        assertEquals(WeatherCondition.Icon.SNOW, WeatherCondition.icon(85));
        assertEquals("Thunderstorm", WeatherCondition.description(99));
    }

    @Test
    public void unknownCodesUseNeutralFallback() {
        assertEquals("Weather", WeatherCondition.description(-1));
        assertEquals(WeatherCondition.Icon.CLOUDY, WeatherCondition.icon(-1));
    }
}
