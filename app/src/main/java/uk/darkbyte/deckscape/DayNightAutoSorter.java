package uk.darkbyte.deckscape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Splits measured wallpapers into a darker Night half and brighter Day half. */
final class DayNightAutoSorter {
    static final class Sample {
        final String name;
        final double luminance;

        Sample(String name, double luminance) {
            this.name = name;
            this.luminance = Double.isFinite(luminance) ? luminance : 0.5;
        }
    }

    private DayNightAutoSorter() {}

    static Map<String, DayNightRole> assign(List<Sample> samples) {
        List<Sample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingDouble((Sample sample) -> sample.luminance)
                .thenComparing(sample -> sample.name));
        Map<String, DayNightRole> result = new LinkedHashMap<>();
        if (sorted.size() == 1) {
            result.put(sorted.get(0).name, DayNightRole.BOTH);
            return result;
        }
        int nightCount = sorted.size() / 2;
        for (int index = 0; index < sorted.size(); index++) {
            result.put(sorted.get(index).name,
                    index < nightCount ? DayNightRole.NIGHT : DayNightRole.DAY);
        }
        return result;
    }
}
