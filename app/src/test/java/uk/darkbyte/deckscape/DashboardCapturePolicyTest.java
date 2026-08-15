package uk.darkbyte.deckscape;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DashboardCapturePolicyTest {
    @Test
    public void acceptsHeadUnitCaptureAndRejectsInvalidDimensions() {
        assertTrue(DashboardCapturePolicy.validDimensions(1_920, 1_080));
        assertFalse(DashboardCapturePolicy.validDimensions(0, 1_080));
        assertFalse(DashboardCapturePolicy.validDimensions(9_000, 1_080));
    }

    @Test
    public void recoversOnlyStaleOrClockInvalidCapture() {
        assertFalse(DashboardCapturePolicy.shouldRecover(20_000, 10_000, 15_000));
        assertTrue(DashboardCapturePolicy.shouldRecover(25_000, 10_000, 15_000));
        assertTrue(DashboardCapturePolicy.shouldRecover(9_000, 10_000, 15_000));
        assertTrue(DashboardCapturePolicy.shouldRecover(20_000, 0, 15_000));
    }

    @Test
    public void calculatesRemainingRecoveryDelay() {
        assertEquals(5_000,
                DashboardCapturePolicy.recoveryDelayMillis(20_000, 10_000, 15_000));
        assertEquals(0,
                DashboardCapturePolicy.recoveryDelayMillis(25_000, 10_000, 15_000));
        assertEquals(0,
                DashboardCapturePolicy.recoveryDelayMillis(9_000, 10_000, 15_000));
    }
}
