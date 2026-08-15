package uk.darkbyte.deckscape;

/** Legacy combined-panel position retained only to migrate the first widget prototype. */
enum OverlayPosition {
    TOP_LEFT("Top left"),
    TOP_RIGHT("Top right"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right");

    final String label;

    OverlayPosition(String label) {
        this.label = label;
    }

    static OverlayPosition parse(String value) {
        if (value != null) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // Fall through to the stable default.
            }
        }
        return TOP_LEFT;
    }
}
