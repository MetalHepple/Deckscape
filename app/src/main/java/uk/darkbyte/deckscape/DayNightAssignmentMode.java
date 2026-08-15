package uk.darkbyte.deckscape;

/** Chooses whether wallpaper Day/Night roles are manual or derived from brightness. */
enum DayNightAssignmentMode {
    MANUAL("Manual"),
    AUTO("Auto by brightness");

    final String label;

    DayNightAssignmentMode(String label) {
        this.label = label;
    }

    static DayNightAssignmentMode parse(String value) {
        try {
            return value == null ? MANUAL : valueOf(value);
        } catch (IllegalArgumentException exception) {
            return MANUAL;
        }
    }
}
