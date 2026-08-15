package uk.darkbyte.deckscape;

/** Resolves the single primary action shown below a wallpaper preview. */
enum PreviewActionState {
    GET("Get", true),
    GET_UNAVAILABLE("Unavailable", false),
    SET("Set", true),
    SELECTED("Selected", false),
    NOW_SHOWING("Now showing", false);

    final String label;
    final boolean enabled;

    PreviewActionState(String label, boolean enabled) {
        this.label = label;
        this.enabled = enabled;
    }

    static PreviewActionState resolve(boolean installed, boolean selected,
                                      boolean wallpaperActive, boolean canGet) {
        if (!installed) return canGet ? GET : GET_UNAVAILABLE;
        if (!selected) return SET;
        return wallpaperActive ? NOW_SHOWING : SELECTED;
    }
}
