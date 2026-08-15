package uk.darkbyte.deckscape;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DayNightAutoSorterTest {
    @Test
    public void emptyLibraryHasNoAssignments() {
        assertTrue(DayNightAutoSorter.assign(Collections.emptyList()).isEmpty());
    }

    @Test
    public void oneWallpaperRemainsEligibleForBothPeriods() {
        Map<String, DayNightRole> result = DayNightAutoSorter.assign(
                Collections.singletonList(new DayNightAutoSorter.Sample("only", 0.2)));
        assertEquals(DayNightRole.BOTH, result.get("only"));
    }

    @Test
    public void darkerHalfIsNightAndBrighterHalfIsDay() {
        Map<String, DayNightRole> result = DayNightAutoSorter.assign(Arrays.asList(
                new DayNightAutoSorter.Sample("brightest", 0.9),
                new DayNightAutoSorter.Sample("darkest", 0.1),
                new DayNightAutoSorter.Sample("bright", 0.7),
                new DayNightAutoSorter.Sample("dark", 0.3)));
        assertEquals(DayNightRole.NIGHT, result.get("darkest"));
        assertEquals(DayNightRole.NIGHT, result.get("dark"));
        assertEquals(DayNightRole.DAY, result.get("bright"));
        assertEquals(DayNightRole.DAY, result.get("brightest"));
    }

    @Test
    public void equalBrightnessUsesStableFilenameOrder() {
        Map<String, DayNightRole> result = DayNightAutoSorter.assign(Arrays.asList(
                new DayNightAutoSorter.Sample("b", 0.5),
                new DayNightAutoSorter.Sample("a", 0.5)));
        assertEquals(DayNightRole.NIGHT, result.get("a"));
        assertEquals(DayNightRole.DAY, result.get("b"));
    }
}
