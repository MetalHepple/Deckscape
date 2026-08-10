package uk.darkbyte.deckscape;

/** The currently active scheduled wallpaper period. */
enum DayPhase {
    DAY("Day"),
    NIGHT("Night");

    final String label;

    DayPhase(String label) {
        this.label = label;
    }
}
