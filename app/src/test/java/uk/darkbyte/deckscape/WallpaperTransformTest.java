package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Verifies every production wallpaper fit mode against a 16:9 canvas. */
public class WallpaperTransformTest {
    @Test
    public void fillCoversCanvasAndCentresCrop() {
        WallpaperTransform.Result result = transform(ScaleMode.FILL, 1, 0.5f, 0.5f);
        assertEquals(1.92f, result.scaleX, 0.001f);
        assertEquals(0f, result.left, 0.001f);
        assertEquals(-420f, result.top, 0.001f);
    }

    @Test
    public void fitShowsWholeImage() {
        WallpaperTransform.Result result = transform(ScaleMode.FIT, 1, 0.5f, 0.5f);
        assertEquals(1.08f, result.scaleX, 0.001f);
        assertEquals(420f, result.left, 0.001f);
        assertEquals(0f, result.top, 0.001f);
    }

    @Test
    public void stretchUsesIndependentAxes() {
        WallpaperTransform.Result result = transform(ScaleMode.STRETCH, 1, 0.5f, 0.5f);
        assertEquals(1.92f, result.scaleX, 0.001f);
        assertEquals(1.08f, result.scaleY, 0.001f);
        assertEquals(0f, result.left, 0.001f);
        assertEquals(0f, result.top, 0.001f);
    }

    @Test
    public void customCropZoomAndFocusRemainBounded() {
        WallpaperTransform.Result start = transform(ScaleMode.CUSTOM, 1.25f, 0, 0);
        WallpaperTransform.Result end = transform(ScaleMode.CUSTOM, 1.25f, 1, 1);
        assertEquals(2.4f, start.scaleX, 0.001f);
        assertEquals(0f, start.left, 0.001f);
        assertEquals(0f, start.top, 0.001f);
        assertEquals(-480f, end.left, 0.001f);
        assertEquals(-1320f, end.top, 0.001f);
    }

    @Test
    public void useDefaultResolvesToConfiguredMode() {
        WallpaperTransform.Result result = WallpaperTransform.calculate(1000, 1000,
                1920, 1080, ScaleMode.DEFAULT, ScaleMode.FIT, 3, 0, 0);
        assertEquals(1.08f, result.scaleX, 0.001f);
        assertEquals(420f, result.left, 0.001f);
    }

    private static WallpaperTransform.Result transform(ScaleMode mode, float zoom,
                                                       float focusX, float focusY) {
        return WallpaperTransform.calculate(1000, 1000, 1920, 1080,
                mode, ScaleMode.FILL, zoom, focusX, focusY);
    }
}
