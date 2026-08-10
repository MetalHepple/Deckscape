package uk.darkbyte.deckscape;

/** Selects automatic environmental scheduling or explicit clock times. */
enum ScheduleMode {
    AUTO("Automatic"),
    MANUAL("Manual times");

    final String label;

    ScheduleMode(String label) {
        this.label = label;
    }

    static ScheduleMode parse(String value) {
        try {
            return value == null ? AUTO : valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AUTO;
        }
    }
}
