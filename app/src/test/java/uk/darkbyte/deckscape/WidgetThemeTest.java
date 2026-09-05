package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public final class WidgetThemeTest {
    @Test
    public void adaptiveStyleUsesContrastForBrightWallpapers() {
        assertSame(WidgetTheme.LIGHT, WidgetTheme.select(true, 0.8));
        assertSame(WidgetTheme.DARK, WidgetTheme.select(true, 0.3));
    }

    @Test
    public void manualDarkStyleIgnoresWallpaperBrightness() {
        assertSame(WidgetTheme.DARK, WidgetTheme.select(false, 0.9));
        assertSame(WidgetTheme.DARK, WidgetTheme.select(true, Double.NaN));
    }
}
