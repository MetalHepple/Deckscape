package uk.darkbyte.deckscape;

/** Converts WMO weather interpretation codes into compact wallpaper-friendly states. */
final class WeatherCondition {
    enum Icon {
        CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM
    }

    private WeatherCondition() {}

    static String description(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45:
            case 48: return "Fog";
            case 51:
            case 53:
            case 55: return "Drizzle";
            case 56:
            case 57: return "Freezing drizzle";
            case 61:
            case 63:
            case 65: return "Rain";
            case 66:
            case 67: return "Freezing rain";
            case 71:
            case 73:
            case 75:
            case 77: return "Snow";
            case 80:
            case 81:
            case 82: return "Rain showers";
            case 85:
            case 86: return "Snow showers";
            case 95:
            case 96:
            case 99: return "Thunderstorm";
            default: return "Weather";
        }
    }

    static Icon icon(int code) {
        if (code == 0 || code == 1) return Icon.CLEAR;
        if (code == 2) return Icon.PARTLY_CLOUDY;
        if (code == 3) return Icon.CLOUDY;
        if (code == 45 || code == 48) return Icon.FOG;
        if (code >= 51 && code <= 57) return Icon.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return Icon.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Icon.SNOW;
        if (code == 95 || code == 96 || code == 99) return Icon.STORM;
        return Icon.CLOUDY;
    }
}
