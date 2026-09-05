package uk.darkbyte.deckscape;

/** Color palette selected from the wallpaper's measured brightness. */
final class WidgetTheme {
    private static final double LIGHT_CARD_THRESHOLD = 0.62;

    static final WidgetTheme DARK = new WidgetTheme(
            0xb80a1118, 0x55ffffff, 0xffffffff, 0xffdbe7ef,
            0xffaebfca, 0xff73dce8, 0xff8da3b1);
    static final WidgetTheme LIGHT = new WidgetTheme(
            0xddeff4f6, 0x55000000, 0xff101820, 0xff304552,
            0xff506671, 0xff006b78, 0xff506671);

    final int card;
    final int border;
    final int primaryText;
    final int secondaryText;
    final int mutedText;
    final int accentText;
    final int creditText;

    private WidgetTheme(int card, int border, int primaryText, int secondaryText,
                        int mutedText, int accentText, int creditText) {
        this.card = card;
        this.border = border;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
        this.mutedText = mutedText;
        this.accentText = accentText;
        this.creditText = creditText;
    }

    static WidgetTheme select(boolean adaptive, double wallpaperLuminance) {
        return adaptive && Double.isFinite(wallpaperLuminance)
                && wallpaperLuminance >= LIGHT_CARD_THRESHOLD ? LIGHT : DARK;
    }
}
