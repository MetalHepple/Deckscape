package uk.darkbyte.deckscape;

/** Recovery only for the unreleased whole-package-disable test; never disables a package. */
final class BydWallpaperLegacyRecovery {
    interface Device {
        int readState() throws Exception;
        void restoreState(int state) throws Exception;
    }

    private BydWallpaperLegacyRecovery() {}

    static boolean restore(Device device, BydWallpaperProtectionTransaction.Journal journal)
            throws Exception {
        int original = journal.previousState();
        if (original == -1) return true;
        if (original != 0 && original != 1) return false;
        int current = device.readState();
        if (current == 3) device.restoreState(original);
        else if (current != original) return false;
        return device.readState() == original && journal.clear();
    }
}
