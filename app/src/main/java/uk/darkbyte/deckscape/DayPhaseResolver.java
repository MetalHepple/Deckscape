package uk.darkbyte.deckscape;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Pure clock and solar calculations used by day/night wallpaper scheduling. */
final class DayPhaseResolver {
    private static final double OFFICIAL_ZENITH_DEGREES = 90.833;

    private DayPhaseResolver() {}

    static DayPhase manual(Instant now, ZoneId zone, int dayMinute, int nightMinute) {
        int minute = ZonedDateTime.ofInstant(now, zone).getHour() * 60
                + ZonedDateTime.ofInstant(now, zone).getMinute();
        int day = normalizeMinute(dayMinute);
        int night = normalizeMinute(nightMinute);
        if (day == night) return DayPhase.DAY;
        boolean daytime = day < night
                ? minute >= day && minute < night
                : minute >= day || minute < night;
        return daytime ? DayPhase.DAY : DayPhase.NIGHT;
    }

    static DayPhase solar(Instant now, ZoneId zone, double latitude, double longitude) {
        SolarEvents events = alignedSolarEvents(now, zone, latitude, longitude);
        if (events == null) return null;
        return !now.isBefore(events.sunrise) && now.isBefore(events.sunset)
                ? DayPhase.DAY : DayPhase.NIGHT;
    }

    /** Returns today's calculated sunrise and sunset as local minutes after midnight. */
    static SolarTimes solarTimes(Instant now, ZoneId zone,
                                 double latitude, double longitude) {
        SolarEvents events = alignedSolarEvents(now, zone, latitude, longitude);
        if (events == null) return null;
        ZonedDateTime sunrise = ZonedDateTime.ofInstant(events.sunrise, zone);
        ZonedDateTime sunset = ZonedDateTime.ofInstant(events.sunset, zone);
        return new SolarTimes(sunrise.getHour() * 60 + sunrise.getMinute(),
                sunset.getHour() * 60 + sunset.getMinute());
    }

    /** Calculates official civil sunrise and sunset without sending location off-device. */
    static SolarEvents solarEvents(LocalDate date, double latitude, double longitude) {
        if (date == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return null;
        }
        Double sunriseHour = eventUtcHour(date, latitude, longitude, true);
        Double sunsetHour = eventUtcHour(date, latitude, longitude, false);
        if (sunriseHour == null || sunsetHour == null) return null;
        Instant base = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant sunrise = base.plusMillis(Math.round(sunriseHour * 3_600_000));
        Instant sunset = base.plusMillis(Math.round(sunsetHour * 3_600_000));
        if (!sunset.isAfter(sunrise)) sunset = sunset.plusSeconds(86_400);
        return new SolarEvents(sunrise, sunset);
    }

    static String formatMinute(int value) {
        int minute = normalizeMinute(value);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static Double eventUtcHour(LocalDate date, double latitude, double longitude,
                                       boolean sunrise) {
        int dayOfYear = date.getDayOfYear();
        double longitudeHour = longitude / 15.0;
        double approximate = dayOfYear
                + ((sunrise ? 6.0 : 18.0) - longitudeHour) / 24.0;
        double meanAnomaly = 0.9856 * approximate - 3.289;
        double trueLongitude = normalizeDegrees(meanAnomaly
                + 1.916 * sinDegrees(meanAnomaly)
                + 0.020 * sinDegrees(2 * meanAnomaly) + 282.634);
        double rightAscension = normalizeDegrees(Math.toDegrees(
                Math.atan(0.91764 * Math.tan(Math.toRadians(trueLongitude)))));
        double longitudeQuadrant = Math.floor(trueLongitude / 90.0) * 90.0;
        double ascensionQuadrant = Math.floor(rightAscension / 90.0) * 90.0;
        rightAscension = (rightAscension + longitudeQuadrant - ascensionQuadrant) / 15.0;

        double sinDeclination = 0.39782 * sinDegrees(trueLongitude);
        double cosDeclination = Math.cos(Math.asin(sinDeclination));
        double cosHour = (Math.cos(Math.toRadians(OFFICIAL_ZENITH_DEGREES))
                - sinDeclination * sinDegrees(latitude))
                / (cosDeclination * Math.cos(Math.toRadians(latitude)));
        if (cosHour > 1 || cosHour < -1) return null;

        double hourAngle = sunrise ? 360.0 - Math.toDegrees(Math.acos(cosHour))
                : Math.toDegrees(Math.acos(cosHour));
        hourAngle /= 15.0;
        double localMeanTime = hourAngle + rightAscension - 0.06571 * approximate - 6.622;
        return normalizeHours(localMeanTime - longitudeHour);
    }

    private static double sinDegrees(double value) {
        return Math.sin(Math.toRadians(value));
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        return normalized < 0 ? normalized + 360.0 : normalized;
    }

    private static double normalizeHours(double value) {
        double normalized = value % 24.0;
        return normalized < 0 ? normalized + 24.0 : normalized;
    }

    private static int normalizeMinute(int value) {
        return Math.floorMod(value, 24 * 60);
    }

    private static SolarEvents alignedSolarEvents(Instant now, ZoneId zone,
                                                   double latitude, double longitude) {
        if (now == null || zone == null) return null;
        LocalDate localDate = ZonedDateTime.ofInstant(now, zone).toLocalDate();
        SolarEvents events = solarEvents(localDate, latitude, longitude);
        if (events == null) return null;
        Instant sunrise = alignToLocalDate(events.sunrise, localDate, zone);
        Instant sunset = alignToLocalDate(events.sunset, localDate, zone);
        if (!sunset.isAfter(sunrise)) sunset = sunset.plusSeconds(86_400);
        return new SolarEvents(sunrise, sunset);
    }

    private static Instant alignToLocalDate(Instant value, LocalDate date, ZoneId zone) {
        Instant result = value;
        LocalDate resultDate = ZonedDateTime.ofInstant(result, zone).toLocalDate();
        while (resultDate.isBefore(date)) {
            result = result.plusSeconds(86_400);
            resultDate = ZonedDateTime.ofInstant(result, zone).toLocalDate();
        }
        while (resultDate.isAfter(date)) {
            result = result.minusSeconds(86_400);
            resultDate = ZonedDateTime.ofInstant(result, zone).toLocalDate();
        }
        return result;
    }

    /** UTC sunrise and sunset instants for one local calendar date. */
    static final class SolarEvents {
        final Instant sunrise;
        final Instant sunset;

        SolarEvents(Instant sunrise, Instant sunset) {
            this.sunrise = sunrise;
            this.sunset = sunset;
        }
    }

    /** Local clock times used by the Settings summary for today's solar schedule. */
    static final class SolarTimes {
        final int sunriseMinute;
        final int sunsetMinute;

        SolarTimes(int sunriseMinute, int sunsetMinute) {
            this.sunriseMinute = normalizeMinute(sunriseMinute);
            this.sunsetMinute = normalizeMinute(sunsetMinute);
        }
    }
}
