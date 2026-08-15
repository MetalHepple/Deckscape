package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Draws passive, glanceable cards after the wallpaper image. */
final class WallpaperOverlayRenderer {
    private static final long DISPLAY_CACHE_MILLIS = 6 * 60 * 60_000L;
    private static final long FRESH_WEATHER_MILLIS = 90 * 60_000L;
    private static final float CLOCK_WIDTH_DP = 210;
    private static final float WEATHER_WIDTH_DP = 270;
    private static final float VEHICLE_BATTERY_WIDTH_DP = 300;
    private static final float VEHICLE_TEMPERATURES_WIDTH_DP = 320;
    private static final float VEHICLE_TYRES_WIDTH_DP = 330;
    private static final float STANDARD_CARD_HEIGHT_DP = 92;
    private static final float VEHICLE_CARD_HEIGHT_DP = 116;
    private static final float EDGE_MARGIN_DP = 24;

    private final Context context;
    private final float density;
    private final float scaledDensity;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Drawable overdriveIcon;
    private long cachedClockMinute = Long.MIN_VALUE;
    private Locale cachedClockLocale;
    private String cachedClockTimeZone = "";
    private boolean cachedClockUses24Hours;
    private String cachedClockTime = "";
    private String cachedClockDate = "";

    WallpaperOverlayRenderer(Context context) {
        this.context = context.getApplicationContext();
        density = context.getResources().getDisplayMetrics().density;
        scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        overdriveIcon = OverdriveBrand.loadInstalledIcon(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    void draw(Canvas canvas, int surfaceWidth, int surfaceHeight,
              EnumSet<OverlayWidget> enabledWidgets,
              Map<OverlayWidget, OverlayPlacement> placements, WeatherSnapshot weather,
              VehicleTelemetrySnapshot vehicle, long nowMillis) {
        if (enabledWidgets == null || enabledWidgets.isEmpty()) return;
        for (OverlayWidget widget : OverlayWidget.values()) {
            if (!enabledWidgets.contains(widget)) continue;
            OverlayPlacement placement = placements == null ? null : placements.get(widget);
            RectF bounds = boundsFor(widget, surfaceWidth, surfaceHeight, placement);
            switch (widget) {
                case CLOCK:
                    drawClock(canvas, bounds.left, bounds.top, bounds.width(), bounds.height(),
                            nowMillis);
                    break;
                case WEATHER:
                    drawWeather(canvas, bounds.left, bounds.top, bounds.width(), bounds.height(),
                            weather, nowMillis);
                    break;
                case VEHICLE_BATTERY:
                    drawVehicleBattery(canvas, bounds, vehicle, nowMillis);
                    break;
                case VEHICLE_TEMPERATURES:
                    drawVehicleTemperatures(canvas, bounds, vehicle, nowMillis);
                    break;
                case VEHICLE_TYRES:
                    drawVehicleTyres(canvas, bounds, vehicle, nowMillis);
                    break;
                default:
                    break;
            }
        }
    }

    RectF clockBounds(int surfaceWidth, int surfaceHeight, OverlayPlacement placement) {
        return boundsFor(OverlayWidget.CLOCK, surfaceWidth, surfaceHeight, placement);
    }

    RectF weatherBounds(int surfaceWidth, int surfaceHeight, OverlayPlacement placement) {
        return boundsFor(OverlayWidget.WEATHER, surfaceWidth, surfaceHeight, placement);
    }

    RectF boundsFor(OverlayWidget widget, int surfaceWidth, int surfaceHeight,
                    OverlayPlacement placement) {
        float widthDp;
        float heightDp;
        switch (widget) {
            case CLOCK:
                widthDp = CLOCK_WIDTH_DP;
                heightDp = STANDARD_CARD_HEIGHT_DP;
                break;
            case WEATHER:
                widthDp = WEATHER_WIDTH_DP;
                heightDp = STANDARD_CARD_HEIGHT_DP;
                break;
            case VEHICLE_BATTERY:
                widthDp = VEHICLE_BATTERY_WIDTH_DP;
                heightDp = VEHICLE_CARD_HEIGHT_DP;
                break;
            case VEHICLE_TEMPERATURES:
                widthDp = VEHICLE_TEMPERATURES_WIDTH_DP;
                heightDp = VEHICLE_CARD_HEIGHT_DP;
                break;
            case VEHICLE_TYRES:
                widthDp = VEHICLE_TYRES_WIDTH_DP;
                heightDp = VEHICLE_CARD_HEIGHT_DP;
                break;
            default:
                throw new IllegalArgumentException("Unknown overlay widget");
        }
        return cardBounds(surfaceWidth, surfaceHeight, placement, dp(widthDp), dp(heightDp));
    }

    private RectF cardBounds(int surfaceWidth, int surfaceHeight, OverlayPlacement placement,
                             float width, float height) {
        OverlayPlacement value = placement == null ? new OverlayPlacement(0.5f, 0.5f)
                : placement;
        float margin = dp(EDGE_MARGIN_DP);
        float left = boundedStart(value.x * surfaceWidth - width / 2f,
                surfaceWidth, width, margin);
        float top = boundedStart(value.y * surfaceHeight - height / 2f,
                surfaceHeight, height, margin);
        return new RectF(left, top, left + width, top + height);
    }

    private static float boundedStart(float desired, float surfaceSize, float cardSize,
                                      float margin) {
        float maximum = surfaceSize - margin - cardSize;
        if (maximum < margin) return (surfaceSize - cardSize) / 2f;
        return Math.max(margin, Math.min(maximum, desired));
    }

    private void drawClock(Canvas canvas, float left, float top, float width, float height,
                           long nowMillis) {
        drawCard(canvas, left, top, width, height);
        updateClockText(nowMillis);
        drawText(canvas, cachedClockTime, left + dp(18), top + dp(47), sp(32), Color.WHITE,
                Paint.Align.LEFT);
        drawText(canvas, cachedClockDate, left + dp(18), top + dp(75), sp(15), 0xffdbe7ef,
                Paint.Align.LEFT);
    }

    /** GIF frames reuse the same formatted strings until a clock input actually changes. */
    private void updateClockText(long nowMillis) {
        Locale locale = Locale.getDefault();
        boolean uses24Hours = DateFormat.is24HourFormat(context);
        String timeZone = TimeZone.getDefault().getID();
        long minute = Math.floorDiv(nowMillis, 60_000L);
        if (minute == cachedClockMinute && locale.equals(cachedClockLocale)
                && uses24Hours == cachedClockUses24Hours
                && timeZone.equals(cachedClockTimeZone)) return;
        Date now = new Date(nowMillis);
        cachedClockTime = new SimpleDateFormat(uses24Hours ? "HH:mm" : "h:mm", locale)
                .format(now);
        cachedClockDate = new SimpleDateFormat("EEE d MMM", locale).format(now);
        cachedClockMinute = minute;
        cachedClockLocale = locale;
        cachedClockUses24Hours = uses24Hours;
        cachedClockTimeZone = timeZone;
    }

    private void drawWeather(Canvas canvas, float left, float top, float width, float height,
                             WeatherSnapshot snapshot, long nowMillis) {
        drawCard(canvas, left, top, width, height);
        boolean displayable = snapshot != null && snapshot.isValid()
                && nowMillis >= snapshot.fetchedAtMillis
                && nowMillis - snapshot.fetchedAtMillis <= DISPLAY_CACHE_MILLIS;
        int code = displayable ? snapshot.weatherCode : -1;
        drawWeatherIcon(canvas, WeatherCondition.icon(code), left + dp(42), top + dp(42));
        String temperature = displayable
                ? Math.round(snapshot.temperatureCelsius) + "\u00b0" : "--\u00b0";
        String description = displayable ? WeatherCondition.description(code)
                : "Weather unavailable";
        if (displayable && nowMillis - snapshot.fetchedAtMillis > FRESH_WEATHER_MILLIS) {
            description += " \u2022 earlier";
        }
        drawText(canvas, temperature, left + dp(78), top + dp(43), sp(28), Color.WHITE,
                Paint.Align.LEFT);
        drawText(canvas, description, left + dp(78), top + dp(66), sp(13), 0xffdbe7ef,
                Paint.Align.LEFT);
        drawText(canvas, "Open-Meteo", left + width - dp(14), top + dp(84), sp(8),
                0xffaebfca, Paint.Align.RIGHT);
    }

    private void drawVehicleBattery(Canvas canvas, RectF bounds,
                                    VehicleTelemetrySnapshot snapshot, long nowMillis) {
        drawCard(canvas, bounds.left, bounds.top, bounds.width(), bounds.height());
        boolean available = snapshot != null && snapshot.isDisplayable(nowMillis);
        drawVehicleHeader(canvas, bounds, "BATTERY");
        String soc = available ? percent(snapshot.socPercent) : "—";
        drawText(canvas, soc, bounds.left + dp(18), bounds.top + dp(71), sp(33), Color.WHITE,
                Paint.Align.LEFT);
        String soh = available && VehicleTelemetrySnapshot.isNumber(snapshot.sohPercent)
                ? "SOH  " + Math.round(snapshot.sohPercent) + "%" : "SOH  —";
        String range = available && VehicleTelemetrySnapshot.isNumber(snapshot.rangeKm)
                ? Math.round(snapshot.rangeKm) + " km range" : "— km range";
        drawText(canvas, soh, bounds.left + dp(128), bounds.top + dp(52), sp(15),
                0xffdbe7ef, Paint.Align.LEFT);
        drawText(canvas, range, bounds.left + dp(128), bounds.top + dp(76), sp(15),
                0xffdbe7ef, Paint.Align.LEFT);
        String details = batteryDetails(available ? snapshot : null);
        drawText(canvas, details, bounds.left + dp(18), bounds.top + dp(101), sp(11),
                0xffaebfca, Paint.Align.LEFT);
        drawProviderCredit(canvas, bounds);
    }

    private void drawVehicleTemperatures(Canvas canvas, RectF bounds,
                                         VehicleTelemetrySnapshot snapshot, long nowMillis) {
        drawCard(canvas, bounds.left, bounds.top, bounds.width(), bounds.height());
        boolean available = snapshot != null && snapshot.isDisplayable(nowMillis);
        drawVehicleHeader(canvas, bounds, "TEMPERATURES");
        drawTemperatureColumn(canvas, bounds, 1f / 6f, "CABIN",
                available ? snapshot.cabinTempC : Double.NaN);
        drawTemperatureColumn(canvas, bounds, 3f / 6f, "OUTSIDE",
                available ? snapshot.outdoorTempC : Double.NaN);
        drawTemperatureColumn(canvas, bounds, 5f / 6f, "BATTERY",
                available ? snapshot.batteryTempC : Double.NaN);
        drawProviderCredit(canvas, bounds);
    }

    private void drawVehicleTyres(Canvas canvas, RectF bounds,
                                  VehicleTelemetrySnapshot snapshot, long nowMillis) {
        drawCard(canvas, bounds.left, bounds.top, bounds.width(), bounds.height());
        boolean available = snapshot != null && snapshot.isDisplayable(nowMillis);
        drawVehicleHeader(canvas, bounds, "TYRES");
        double low = available ? snapshot.tyreLowBar : Double.NaN;
        double high = available ? snapshot.tyreHighBar : Double.NaN;
        drawTyreColumn(canvas, bounds, 1f / 8f, "FL",
                available ? snapshot.tyreFrontLeftBar : Double.NaN,
                available ? snapshot.tyreFrontLeftTempC : Double.NaN, low, high);
        drawTyreColumn(canvas, bounds, 3f / 8f, "FR",
                available ? snapshot.tyreFrontRightBar : Double.NaN,
                available ? snapshot.tyreFrontRightTempC : Double.NaN, low, high);
        drawTyreColumn(canvas, bounds, 5f / 8f, "RL",
                available ? snapshot.tyreRearLeftBar : Double.NaN,
                available ? snapshot.tyreRearLeftTempC : Double.NaN, low, high);
        drawTyreColumn(canvas, bounds, 7f / 8f, "RR",
                available ? snapshot.tyreRearRightBar : Double.NaN,
                available ? snapshot.tyreRearRightTempC : Double.NaN, low, high);
        drawText(canvas, "bar", bounds.left + dp(16), bounds.top + dp(102), sp(10),
                0xffaebfca, Paint.Align.LEFT);
        drawProviderCredit(canvas, bounds);
    }

    private void drawVehicleHeader(Canvas canvas, RectF bounds, String title) {
        float textLeft = bounds.left + dp(16);
        if (overdriveIcon != null) {
            int iconSize = Math.round(dp(19));
            int iconLeft = Math.round(bounds.left + dp(14));
            int iconTop = Math.round(bounds.top + dp(7));
            overdriveIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize,
                    iconTop + iconSize);
            overdriveIcon.draw(canvas);
            textLeft = bounds.left + dp(40);
        }
        drawText(canvas, "OVERDRIVE  •  " + title, textLeft, bounds.top + dp(22), sp(11),
                0xff73dce8, Paint.Align.LEFT);
    }

    private void drawProviderCredit(Canvas canvas, RectF bounds) {
        drawText(canvas, "Overdrive data", bounds.right - dp(12), bounds.bottom - dp(9), sp(8),
                0xff8da3b1, Paint.Align.RIGHT);
    }

    private void drawTemperatureColumn(Canvas canvas, RectF bounds, float fraction,
                                       String label, double value) {
        float center = bounds.left + bounds.width() * fraction;
        drawText(canvas, label, center, bounds.top + dp(48), sp(10), 0xffaebfca,
                Paint.Align.CENTER);
        String reading = VehicleTelemetrySnapshot.isNumber(value)
                ? Math.round(value) + "°" : "—°";
        drawText(canvas, reading, center, bounds.top + dp(80), sp(25), Color.WHITE,
                Paint.Align.CENTER);
    }

    private void drawTyreColumn(Canvas canvas, RectF bounds, float fraction, String label,
                                double value, double temperature, double low, double high) {
        float center = bounds.left + bounds.width() * fraction;
        drawText(canvas, label, center, bounds.top + dp(48), sp(10), 0xffaebfca,
                Paint.Align.CENTER);
        int color = Color.WHITE;
        if (VehicleTelemetrySnapshot.isNumber(value)
                && VehicleTelemetrySnapshot.isNumber(low)
                && VehicleTelemetrySnapshot.isNumber(high)
                && (value < low || value > high)) {
            color = 0xffff7657;
        }
        String reading = VehicleTelemetrySnapshot.isNumber(value)
                ? String.format(Locale.ROOT, "%.2f", value) : "—";
        drawText(canvas, reading, center, bounds.top + dp(72), sp(18), color,
                Paint.Align.CENTER);
        String thermal = VehicleTelemetrySnapshot.isNumber(temperature)
                ? Math.round(temperature) + "°" : "—°";
        drawText(canvas, thermal, center, bounds.top + dp(92), sp(11), 0xffaebfca,
                Paint.Align.CENTER);
    }

    private static String percent(double value) {
        return VehicleTelemetrySnapshot.isNumber(value) ? Math.round(value) + "%" : "—";
    }

    private static String batteryDetails(VehicleTelemetrySnapshot snapshot) {
        if (snapshot == null) return "Waiting for Overdrive";
        StringBuilder value = new StringBuilder();
        if (snapshot.chargingKnown && snapshot.charging) {
            value.append("Charging");
            if (VehicleTelemetrySnapshot.isNumber(snapshot.chargingKw)
                    && snapshot.chargingKw > 0) {
                value.append(' ').append(String.format(Locale.ROOT, "%.1f kW",
                        snapshot.chargingKw));
            }
        }
        if (VehicleTelemetrySnapshot.isNumber(snapshot.voltage12v)) {
            if (value.length() > 0) value.append("  •  ");
            value.append(String.format(Locale.ROOT, "12 V  %.1f V", snapshot.voltage12v));
        }
        if (VehicleTelemetrySnapshot.isNumber(snapshot.remainingKwh)) {
            if (value.length() > 0) value.append("  •  ");
            value.append(String.format(Locale.ROOT, "%.1f kWh left", snapshot.remainingKwh));
        }
        return value.length() == 0 ? "Vehicle data unavailable" : value.toString();
    }

    private void drawCard(Canvas canvas, float left, float top, float width, float height) {
        RectF bounds = new RectF(left, top, left + width, top + height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xb80a1118);
        canvas.drawRoundRect(bounds, dp(16), dp(16), paint);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x55ffffff);
        canvas.drawRoundRect(bounds, dp(16), dp(16), stroke);
    }

    private void drawText(Canvas canvas, String text, float x, float baseline, float size,
                          int color, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(null);
        paint.setTextAlign(align);
        paint.setTextSize(size);
        paint.setColor(color);
        canvas.drawText(text, x, baseline, paint);
    }

    private void drawWeatherIcon(Canvas canvas, WeatherCondition.Icon icon, float centerX,
                                 float centerY) {
        stroke.setStrokeWidth(dp(2.4f));
        stroke.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffdbe7ef);
        if (icon == WeatherCondition.Icon.CLEAR) {
            drawSun(canvas, centerX, centerY);
            return;
        }
        if (icon == WeatherCondition.Icon.PARTLY_CLOUDY) {
            drawSun(canvas, centerX - dp(10), centerY - dp(9));
        }
        drawCloud(canvas, centerX, centerY - dp(2));
        if (icon == WeatherCondition.Icon.FOG) {
            canvas.drawLine(centerX - dp(20), centerY + dp(15),
                    centerX + dp(20), centerY + dp(15), stroke);
            canvas.drawLine(centerX - dp(15), centerY + dp(22),
                    centerX + dp(16), centerY + dp(22), stroke);
        } else if (icon == WeatherCondition.Icon.DRIZZLE
                || icon == WeatherCondition.Icon.RAIN) {
            int drops = icon == WeatherCondition.Icon.RAIN ? 3 : 2;
            for (int index = 0; index < drops; index++) {
                float x = centerX + dp((index - (drops - 1) / 2f) * 12);
                canvas.drawLine(x, centerY + dp(13), x - dp(3), centerY + dp(21), stroke);
            }
        } else if (icon == WeatherCondition.Icon.SNOW) {
            for (int index = -1; index <= 1; index++) {
                float x = centerX + dp(index * 13);
                canvas.drawCircle(x, centerY + dp(18), dp(2), paint);
            }
        } else if (icon == WeatherCondition.Icon.STORM) {
            path.reset();
            path.moveTo(centerX + dp(2), centerY + dp(10));
            path.lineTo(centerX - dp(5), centerY + dp(22));
            path.lineTo(centerX + dp(3), centerY + dp(20));
            path.lineTo(centerX - dp(1), centerY + dp(30));
            path.lineTo(centerX + dp(11), centerY + dp(16));
            path.lineTo(centerX + dp(4), centerY + dp(17));
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    private void drawSun(Canvas canvas, float centerX, float centerY) {
        canvas.drawCircle(centerX, centerY, dp(9), paint);
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4;
            float innerX = centerX + (float) Math.cos(angle) * dp(14);
            float innerY = centerY + (float) Math.sin(angle) * dp(14);
            float outerX = centerX + (float) Math.cos(angle) * dp(20);
            float outerY = centerY + (float) Math.sin(angle) * dp(20);
            canvas.drawLine(innerX, innerY, outerX, outerY, stroke);
        }
    }

    private void drawCloud(Canvas canvas, float centerX, float centerY) {
        canvas.drawCircle(centerX - dp(10), centerY + dp(2), dp(10), paint);
        canvas.drawCircle(centerX + dp(2), centerY - dp(5), dp(14), paint);
        canvas.drawCircle(centerX + dp(16), centerY + dp(2), dp(10), paint);
        canvas.drawRect(centerX - dp(10), centerY, centerX + dp(16),
                centerY + dp(12), paint);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }
}
