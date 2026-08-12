package uk.darkbyte.deckscape;

/** Filters the downloaded library without changing a wallpaper's stored Day & Night role. */
enum LibraryGroup {
    ALL("All"),
    DAY("Day"),
    NIGHT("Night");

    final String label;

    LibraryGroup(String label) {
        this.label = label;
    }

    /** Both-role wallpapers intentionally appear in both scheduled groups. */
    boolean includes(DayNightRole role) {
        if (this == ALL) return true;
        DayNightRole value = role == null ? DayNightRole.BOTH : role;
        return value == DayNightRole.BOTH
                || (this == DAY && value == DayNightRole.DAY)
                || (this == NIGHT && value == DayNightRole.NIGHT);
    }
}
