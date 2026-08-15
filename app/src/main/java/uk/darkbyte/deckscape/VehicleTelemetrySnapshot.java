package uk.darkbyte.deckscape;

/** Validated, provider-neutral vehicle values. Missing numeric values are NaN. */
final class VehicleTelemetrySnapshot {
    static final long DISPLAY_MAX_AGE_MILLIS = 5 * 60_000L;

    final long fetchedAtMillis;
    final boolean vehicleDataReady;
    final double socPercent;
    final double sohPercent;
    final double remainingKwh;
    final double voltage12v;
    final double rangeKm;
    final boolean charging;
    final boolean chargingKnown;
    final double chargingKw;
    final double cabinTempC;
    final double outdoorTempC;
    final double batteryTempC;
    final double tyreFrontLeftBar;
    final double tyreFrontRightBar;
    final double tyreRearLeftBar;
    final double tyreRearRightBar;
    final double tyreFrontLeftTempC;
    final double tyreFrontRightTempC;
    final double tyreRearLeftTempC;
    final double tyreRearRightTempC;
    final double tyreLowBar;
    final double tyreHighBar;

    private VehicleTelemetrySnapshot(Builder builder) {
        fetchedAtMillis = builder.fetchedAtMillis;
        vehicleDataReady = builder.vehicleDataReady;
        socPercent = percentOrNaN(builder.socPercent, true);
        sohPercent = percentOrNaN(builder.sohPercent, false);
        remainingKwh = boundedOrNaN(builder.remainingKwh, 0, 250);
        voltage12v = boundedOrNaN(builder.voltage12v, 5, 20);
        rangeKm = boundedOrNaN(builder.rangeKm, 0, 2_500);
        charging = builder.charging;
        chargingKnown = builder.chargingKnown;
        chargingKw = boundedOrNaN(builder.chargingKw, -500, 500);
        cabinTempC = boundedOrNaN(builder.cabinTempC, -50, 90);
        outdoorTempC = boundedOrNaN(builder.outdoorTempC, -50, 60);
        batteryTempC = boundedOrNaN(builder.batteryTempC, -40, 100);
        tyreFrontLeftBar = tyreOrNaN(builder.tyreFrontLeftBar);
        tyreFrontRightBar = tyreOrNaN(builder.tyreFrontRightBar);
        tyreRearLeftBar = tyreOrNaN(builder.tyreRearLeftBar);
        tyreRearRightBar = tyreOrNaN(builder.tyreRearRightBar);
        tyreFrontLeftTempC = boundedOrNaN(builder.tyreFrontLeftTempC, -50, 120);
        tyreFrontRightTempC = boundedOrNaN(builder.tyreFrontRightTempC, -50, 120);
        tyreRearLeftTempC = boundedOrNaN(builder.tyreRearLeftTempC, -50, 120);
        tyreRearRightTempC = boundedOrNaN(builder.tyreRearRightTempC, -50, 120);
        tyreLowBar = tyreOrNaN(builder.tyreLowBar);
        tyreHighBar = tyreOrNaN(builder.tyreHighBar);
    }

    boolean isDisplayable(long nowMillis) {
        return fetchedAtMillis > 0 && nowMillis >= fetchedAtMillis
                && nowMillis - fetchedAtMillis <= DISPLAY_MAX_AGE_MILLIS;
    }

    boolean hasAnyValue() {
        return isNumber(socPercent) || isNumber(sohPercent) || isNumber(remainingKwh)
                || isNumber(voltage12v) || isNumber(rangeKm) || isNumber(cabinTempC)
                || isNumber(outdoorTempC) || isNumber(batteryTempC)
                || isNumber(tyreFrontLeftBar) || isNumber(tyreFrontRightBar)
                || isNumber(tyreRearLeftBar) || isNumber(tyreRearRightBar)
                || isNumber(tyreFrontLeftTempC) || isNumber(tyreFrontRightTempC)
                || isNumber(tyreRearLeftTempC) || isNumber(tyreRearRightTempC);
    }

    private static double percentOrNaN(double value, boolean allowZero) {
        if (!isNumber(value) || value > 100 || value < 0 || (!allowZero && value == 0)) {
            return Double.NaN;
        }
        return value;
    }

    private static double tyreOrNaN(double value) {
        return boundedOrNaN(value, 0.5, 5);
    }

    private static double boundedOrNaN(double value, double minimum, double maximum) {
        return isNumber(value) && value >= minimum && value <= maximum
                ? value : Double.NaN;
    }

    static boolean isNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    static final class Builder {
        long fetchedAtMillis;
        boolean vehicleDataReady;
        double socPercent = Double.NaN;
        double sohPercent = Double.NaN;
        double remainingKwh = Double.NaN;
        double voltage12v = Double.NaN;
        double rangeKm = Double.NaN;
        boolean charging;
        boolean chargingKnown;
        double chargingKw = Double.NaN;
        double cabinTempC = Double.NaN;
        double outdoorTempC = Double.NaN;
        double batteryTempC = Double.NaN;
        double tyreFrontLeftBar = Double.NaN;
        double tyreFrontRightBar = Double.NaN;
        double tyreRearLeftBar = Double.NaN;
        double tyreRearRightBar = Double.NaN;
        double tyreFrontLeftTempC = Double.NaN;
        double tyreFrontRightTempC = Double.NaN;
        double tyreRearLeftTempC = Double.NaN;
        double tyreRearRightTempC = Double.NaN;
        double tyreLowBar = Double.NaN;
        double tyreHighBar = Double.NaN;

        VehicleTelemetrySnapshot build() {
            return new VehicleTelemetrySnapshot(this);
        }
    }
}
