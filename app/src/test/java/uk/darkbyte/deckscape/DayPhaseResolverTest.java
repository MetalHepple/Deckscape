package uk.darkbyte.deckscape;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Covers manual boundaries, solar events, and polar fallback behaviour. */
public class DayPhaseResolverTest {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Test
    public void manualScheduleChangesAtExactBoundaries() {
        assertEquals(DayPhase.NIGHT, DayPhaseResolver.manual(
                Instant.parse("2026-08-10T05:59:00Z"), LONDON, 7 * 60, 19 * 60));
        assertEquals(DayPhase.DAY, DayPhaseResolver.manual(
                Instant.parse("2026-08-10T06:00:00Z"), LONDON, 7 * 60, 19 * 60));
        assertEquals(DayPhase.NIGHT, DayPhaseResolver.manual(
                Instant.parse("2026-08-10T18:00:00Z"), LONDON, 7 * 60, 19 * 60));
    }

    @Test
    public void wrappedManualDayPeriodWorksAcrossMidnight() {
        assertEquals(DayPhase.DAY, DayPhaseResolver.manual(
                Instant.parse("2026-01-01T23:00:00Z"), ZoneId.of("UTC"), 20 * 60, 6 * 60));
        assertEquals(DayPhase.NIGHT, DayPhaseResolver.manual(
                Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"), 20 * 60, 6 * 60));
    }

    @Test
    public void londonSummerProducesDayAndNight() {
        assertNotNull(DayPhaseResolver.solarEvents(LocalDate.of(2026, 6, 21),
                51.5, -0.1));
        assertEquals(DayPhase.DAY, DayPhaseResolver.solar(
                Instant.parse("2026-06-21T12:00:00Z"), LONDON, 51.5, -0.1));
        assertEquals(DayPhase.NIGHT, DayPhaseResolver.solar(
                Instant.parse("2026-06-21T00:00:00Z"), LONDON, 51.5, -0.1));
    }

    @Test
    public void solarTimesUseTheSameLocalBoundariesAsPhaseResolution() {
        Instant midday = Instant.parse("2026-08-12T12:00:00Z");
        DayPhaseResolver.SolarTimes times = DayPhaseResolver.solarTimes(
                midday, LONDON, 51.5, -0.1);

        assertNotNull(times);
        assertTrue(times.sunriseMinute >= 5 * 60 && times.sunriseMinute < 6 * 60);
        assertTrue(times.sunsetMinute >= 20 * 60 && times.sunsetMinute < 21 * 60);
        Instant sunrise = LocalDate.of(2026, 8, 12).atStartOfDay(LONDON)
                .plusMinutes(times.sunriseMinute).toInstant();
        Instant sunset = LocalDate.of(2026, 8, 12).atStartOfDay(LONDON)
                .plusMinutes(times.sunsetMinute).toInstant();
        assertEquals(DayPhase.NIGHT, DayPhaseResolver.solar(
                sunrise.minusSeconds(60), LONDON, 51.5, -0.1));
        assertEquals(DayPhase.DAY, DayPhaseResolver.solar(
                sunrise.plusSeconds(60), LONDON, 51.5, -0.1));
        assertEquals(DayPhase.DAY, DayPhaseResolver.solar(
                sunset.minusSeconds(60), LONDON, 51.5, -0.1));
        assertEquals(DayPhase.NIGHT, DayPhaseResolver.solar(
                sunset.plusSeconds(60), LONDON, 51.5, -0.1));
        assertEquals(DayPhase.DAY, DayPhaseResolver.solar(
                midday, LONDON, 51.5, -0.1));
    }

    @Test
    public void solarTimesReturnNullWhenNoEventExists() {
        assertNull(DayPhaseResolver.solarTimes(Instant.parse("2026-06-21T12:00:00Z"),
                ZoneId.of("UTC"), 89, 0));
    }

    @Test
    public void polarNoEventReturnsNullForManualFallback() {
        assertNull(DayPhaseResolver.solarEvents(LocalDate.of(2026, 6, 21), 89, 0));
    }

    @Test
    public void solarPhaseAlignsEventsToFarEasternLocalDate() {
        ZoneId auckland = ZoneId.of("Pacific/Auckland");
        assertEquals(DayPhase.DAY, DayPhaseResolver.solar(
                Instant.parse("2026-01-15T00:00:00Z"), auckland, -36.85, 174.76));
        assertEquals(DayPhase.NIGHT, DayPhaseResolver.solar(
                Instant.parse("2026-01-15T11:00:00Z"), auckland, -36.85, 174.76));
    }
}
