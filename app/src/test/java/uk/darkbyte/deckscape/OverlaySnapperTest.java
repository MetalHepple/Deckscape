package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OverlaySnapperTest {
    @Test
    public void snapsEachNearbyAxisIndependently() {
        OverlaySnapper.Result result = OverlaySnapper.snap(
                510, 680, 500, 600, 40);
        assertEquals(500, result.x, 0);
        assertEquals(680, result.y, 0);
        assertTrue(result.snappedX);
        assertFalse(result.snappedY);
    }

    @Test
    public void snapsBothAxesAtThreshold() {
        OverlaySnapper.Result result = OverlaySnapper.snap(
                540, 560, 500, 600, 40);
        assertEquals(500, result.x, 0);
        assertEquals(600, result.y, 0);
        assertTrue(result.snappedX);
        assertTrue(result.snappedY);
    }

    @Test
    public void independentlyChoosesTheNearestGuideAcrossSeveralWidgets() {
        OverlaySnapper.Result result = OverlaySnapper.snapToNearest(
                210, 390, new float[]{100, 200, 500}, new float[]{50, 400, 700}, 40);
        assertEquals(200, result.x, 0f);
        assertEquals(400, result.y, 0f);
        assertEquals(200, result.guideX, 0f);
        assertEquals(400, result.guideY, 0f);
        assertTrue(result.snappedX);
        assertTrue(result.snappedY);
    }
}
