package uk.darkbyte.deckscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class BydWallpaperLegacyRecoveryTest {
    private final Fake device = new Fake();

    @Test public void noReceiptNeverTouchesPackage() throws Exception {
        device.original = -1;
        assertTrue(recover());
        assertEquals(0, device.reads);
        assertEquals(0, device.writes);
    }
    @Test public void ownedDisableRestoresAndVerifiesOriginal() throws Exception {
        for (int original : new int[]{0, 1}) {
            device.original = original;
            device.state = 3;
            assertTrue(recover());
            assertEquals(original, device.state);
            assertEquals(-1, device.original);
        }
    }
    @Test public void failedRestoreKeepsReceipt() throws Exception {
        device.ignoreWrites = true;
        assertFalse(recover());
        assertEquals(0, device.original);
    }
    @Test public void externalChangeIsNotOverwritten() throws Exception {
        device.state = 2;
        assertFalse(recover());
        assertEquals(0, device.writes);
    }
    @Test public void corruptedReceiptIsRejected() throws Exception {
        device.original = 99;
        assertFalse(recover());
        assertEquals(0, device.reads);
    }
    @Test public void lostClearCanBeRetriedWithoutRewritingPackage() throws Exception {
        device.clearFails = true;
        assertFalse(recover());
        assertEquals(0, device.state);
        device.clearFails = false;
        assertTrue(recover());
        assertEquals(1, device.writes);
    }
    private boolean recover() throws Exception { return BydWallpaperLegacyRecovery.restore(device, device); }
    private static final class Fake implements BydWallpaperLegacyRecovery.Device,
            BydWallpaperProtectionTransaction.Journal {
        int original;
        int state = 3;
        int reads;
        int writes;
        boolean ignoreWrites;
        boolean clearFails;
        @Override public int readState() { reads++; return state; }
        @Override public void restoreState(int value) { writes++; if (!ignoreWrites) state = value; }
        @Override public int previousState() { return original; }
        @Override public boolean save(int value) { throw new AssertionError("Recovery must not create receipts"); }
        @Override public boolean clear() { if (clearFails) return false; original = -1; return true; }
    }
}
