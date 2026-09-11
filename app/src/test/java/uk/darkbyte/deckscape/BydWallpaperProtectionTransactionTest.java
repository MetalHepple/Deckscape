package uk.darkbyte.deckscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BydWallpaperProtectionTransactionTest {
    private final FakeJournal journal = new FakeJournal();
    private final FakeDevice device = new FakeDevice(journal);

    @Test public void savesOriginalBeforeBlockingAndVerifiesDurableReadback() throws Exception {
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, apply());
        assertEquals(0, journal.previous);
        assertEquals(1, device.state);
        assertEquals(1, device.writes);
        assertEquals(1, device.flushes);
    }

    @Test public void repeatedActivationNeedsNoFurtherWrite() throws Exception {
        apply();
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, apply());
        assertEquals(1, device.writes);
        assertEquals(0, journal.previous);
    }

    @Test public void externalPermissionChangeDoesNotOverwriteOriginal() throws Exception {
        device.state = 3;
        apply();
        device.state = 0;
        apply();
        assertEquals(3, journal.previous);
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, restore());
        assertEquals(3, device.state);
        assertEquals(-1, journal.previous);
    }

    @Test public void inactiveWallpaperIsNeverChanged() throws Exception {
        device.active = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.NOT_ACTIVE, apply());
        assertEquals(0, device.writes);
        assertEquals(-1, journal.previous);
    }

    @Test public void selectionChangingDuringSetupRollsBack() throws Exception {
        device.loseSelectionOnWrite = true;
        assertEquals(BydWallpaperProtectionTransaction.Result.NOT_ACTIVE, apply());
        assertEquals(0, device.state);
        assertEquals(-1, journal.previous);
    }

    @Test public void failedJournalWritePreventsDeviceChange() throws Exception {
        journal.writable = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, apply());
        assertEquals(0, device.writes);
    }

    @Test public void failedRestrictionIsNotReportedAsProtected() throws Exception {
        device.ignoreWrites = true;
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, apply());
        assertEquals(0, journal.previous);
    }

    @Test public void uncertainRestrictionCanBeRecoveredAfterReconnection() throws Exception {
        device.throwAfterWrite = true;
        try { apply(); } catch (IllegalStateException expected) { /* Lost ADB response. */ }
        assertEquals(1, device.state);
        assertEquals(0, journal.previous);
        device.throwAfterWrite = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, apply());
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, restore());
        assertEquals(0, device.state);
    }

    @Test public void externalRestrictionIsNotClaimedOrModified() throws Exception {
        device.state = 1;
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, apply());
        assertEquals(-1, journal.previous);
        assertEquals(0, device.writes);
    }

    @Test public void lostResponseThenLostSelectionStillRestoresOnRetry() throws Exception {
        device.throwAfterWrite = true;
        try { apply(); } catch (IllegalStateException expected) { /* Lost response. */ }
        device.throwAfterWrite = false;
        device.active = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.NOT_ACTIVE, apply());
        assertEquals(0, device.state);
        assertEquals(-1, journal.previous);
    }

    @Test public void explicitRestoreWorksAfterClearedAppData() throws Exception {
        device.state = 1;
        device.active = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, restore());
        assertEquals(0, device.state);
    }

    @Test public void failedRestoreRetainsRecoveryRecord() throws Exception {
        apply();
        device.ignoreWrites = true;
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, restore());
        assertEquals(0, journal.previous);
    }

    @Test public void unreadableOrUnexpectedStateFailsClosed() throws Exception {
        device.state = -1;
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, apply());
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, restore());
        device.state = 5;
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, apply());
        assertEquals(0, device.writes);
    }

    @Test public void corruptedJournalCannotDriveAnOperationWrite() throws Exception {
        journal.previous = 99;
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, apply());
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, restore());
        assertEquals(0, device.writes);
    }

    @Test public void failedJournalClearKeepsRestoreRetryable() throws Exception {
        apply();
        journal.writable = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.FAILED, restore());
        assertEquals(0, journal.previous);
        assertEquals(0, device.state);
        journal.writable = true;
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, restore());
    }

    @Test public void allSupportedOriginalModesAreRestoredExactly() throws Exception {
        for (int original : new int[]{0, 2, 3, 4}) {
            device.state = original;
            assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, apply());
            assertEquals(original, journal.previous);
            assertEquals(1, device.state);
            assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, restore());
            assertEquals(original, device.state);
            assertEquals(-1, journal.previous);
        }
    }

    @Test public void lostFlushCannotBeReportedAsDurableAndRetryFlushesAgain() throws Exception {
        device.failFlush = true;
        try { apply(); } catch (IllegalStateException expected) { /* Storage/connection failure. */ }
        assertEquals(1, device.state);
        assertEquals(0, journal.previous);
        device.failFlush = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, apply());
        assertEquals(2, device.flushes);
        assertEquals(1, device.writes);
    }

    @Test public void failedRestoreFlushRetainsOriginalUntilDurableRetry() throws Exception {
        apply();
        device.failFlush = true;
        try { restore(); } catch (IllegalStateException expected) { /* Retry must retain receipt. */ }
        assertEquals(0, journal.previous);
        device.failFlush = false;
        assertEquals(BydWallpaperProtectionTransaction.Result.APPLIED, restore());
        assertEquals(-1, journal.previous);
    }

    @Test public void selectionLostDuringReconnectFlushRestoresOwnedRestriction() throws Exception {
        apply();
        device.loseSelectionOnFlush = true;
        assertEquals(BydWallpaperProtectionTransaction.Result.NOT_ACTIVE, apply());
        assertEquals(0, device.state);
        assertEquals(-1, journal.previous);
    }

    private BydWallpaperProtectionTransaction.Result apply() throws Exception {
        return BydWallpaperProtectionTransaction.apply(device, journal);
    }

    private BydWallpaperProtectionTransaction.Result restore() throws Exception {
        return BydWallpaperProtectionTransaction.restore(device, journal);
    }

    private static final class FakeJournal implements BydWallpaperProtectionTransaction.Journal {
        int previous = -1;
        boolean writable = true;
        @Override public int previousState() { return previous; }
        @Override public boolean save(int state) {
            if (!writable) return false;
            previous = state;
            return true;
        }
        @Override public boolean clear() {
            if (!writable) return false;
            previous = -1;
            return true;
        }
    }

    private static final class FakeDevice implements BydWallpaperProtectionTransaction.Device {
        final FakeJournal journal;
        int state;
        int writes;
        int flushes;
        boolean active = true;
        boolean ignoreWrites;
        boolean loseSelectionOnWrite;
        boolean throwAfterWrite;
        boolean failFlush;
        boolean loseSelectionOnFlush;
        FakeDevice(FakeJournal journal) { this.journal = journal; }
        @Override public boolean isDeckscapeActive() { return active; }
        @Override public int readState() { return state; }
        @Override public void setState(int value) {
            if (value == 1) assertTrue(journal.previous >= 0 && journal.previous <= 4 && journal.previous != 1);
            writes++;
            if (!ignoreWrites) state = value;
            if (loseSelectionOnWrite) active = false;
            if (throwAfterWrite) throw new IllegalStateException("Connection lost after mutation");
        }
        @Override public void persistState() {
            flushes++;
            if (loseSelectionOnFlush) active = false;
            if (failFlush) throw new IllegalStateException("Flush failed");
        }
    }
}
