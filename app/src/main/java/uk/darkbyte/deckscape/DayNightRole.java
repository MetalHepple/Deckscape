package uk.darkbyte.deckscape;

/** Assigns a downloaded wallpaper to one or both scheduled periods. */
enum DayNightRole {
    BOTH("Day & night"),
    DAY("Day only"),
    NIGHT("Night only");

    final String label;

    DayNightRole(String label) {
        this.label = label;
    }

    boolean isEligible(DayPhase phase) {
        return this == BOTH || (this == DAY && phase == DayPhase.DAY)
                || (this == NIGHT && phase == DayPhase.NIGHT);
    }

    /** Returns the next role used by the Library's quick-cycle control. */
    DayNightRole next() {
        switch (this) {
            case BOTH:
                return DAY;
            case DAY:
                return NIGHT;
            case NIGHT:
            default:
                return BOTH;
        }
    }

    static DayNightRole parse(String value) {
        try {
            return value == null ? BOTH : valueOf(value);
        } catch (IllegalArgumentException exception) {
            return BOTH;
        }
    }
}
