package uk.darkbyte.deckscape;

/** Describes how a wallpaper is mapped onto the live-wallpaper canvas. */
enum ScaleMode {
    DEFAULT("Use default"),
    FILL("Fill screen"),
    FIT("Fit whole image"),
    STRETCH("Stretch to screen"),
    CUSTOM("Custom crop");

    final String label;

    ScaleMode(String label) {
        this.label = label;
    }

    static ScaleMode parse(String value, ScaleMode fallback) {
        try {
            return value == null ? fallback : valueOf(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
