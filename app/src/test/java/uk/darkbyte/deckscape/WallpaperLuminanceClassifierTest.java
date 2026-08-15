package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WallpaperLuminanceClassifierTest {
    @Test
    public void blackAndWhiteBoundTheScale() {
        assertEquals(0, WallpaperLuminanceClassifier.meanLuminance(
                new int[]{0xff000000}), 0.0001);
        assertEquals(1, WallpaperLuminanceClassifier.meanLuminance(
                new int[]{0xffffffff}), 0.0001);
    }

    @Test
    public void perceivedWeightingValuesGreenAboveRedAboveBlue() {
        double red = WallpaperLuminanceClassifier.meanLuminance(new int[]{0xffff0000});
        double green = WallpaperLuminanceClassifier.meanLuminance(new int[]{0xff00ff00});
        double blue = WallpaperLuminanceClassifier.meanLuminance(new int[]{0xff0000ff});
        assertTrue(green > red);
        assertTrue(red > blue);
    }

    @Test
    public void transparentPixelsDoNotDarkenTheMeasurement() {
        assertEquals(1, WallpaperLuminanceClassifier.meanLuminance(
                new int[]{0x00000000, 0xffffffff}), 0.0001);
    }
}
