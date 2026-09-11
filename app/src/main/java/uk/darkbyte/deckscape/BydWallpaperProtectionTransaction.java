package uk.darkbyte.deckscape;

/** A package-only wallpaper AppOp change, with a write-ahead recovery record. */
final class BydWallpaperProtectionTransaction {
    enum Result { APPLIED, NOT_ACTIVE, FAILED }

    interface Device {
        boolean isDeckscapeActive();
        int readState() throws Exception;
        void setState(int state) throws Exception;
        void persistState() throws Exception;
    }

    interface Journal {
        int previousState();
        boolean save(int state);
        boolean clear();
    }

    private BydWallpaperProtectionTransaction() {}

    static Result apply(Device device, Journal journal) throws Exception {
        if (!device.isDeckscapeActive()) {
            // Also covers a lost ADB response after disabling, followed by a user switch
            // before the retry. A saved receipt must remain usable on that path.
            if (journal.previousState() != -1 && device.readState() == 1) {
                return restore(device, journal) == Result.APPLIED ? Result.NOT_ACTIVE : Result.FAILED;
            }
            return Result.NOT_ACTIVE;
        }
        int state = device.readState();
        if (state < 0 || state > 4) return Result.FAILED;
        int previous = journal.previousState();
        if (previous < -1 || previous > 4 || previous == 1) return Result.FAILED;
        if (state == 1) {
            // A connection may have failed after the mode changed but before disk flush.
            device.persistState();
            if (device.readState() != 1) return Result.FAILED;
            if (!device.isDeckscapeActive()) {
                return previous == -1 || restore(device, journal) == Result.APPLIED
                        ? Result.NOT_ACTIVE : Result.FAILED;
            }
            return Result.APPLIED;
        }
        // Never overwrite the original state during retries or after a process restart.
        if (previous == -1 && !journal.save(state)) return Result.FAILED;
        if (!device.isDeckscapeActive()) return Result.NOT_ACTIVE;
        device.setState(1);
        if (device.readState() != 1) return Result.FAILED;
        device.persistState();
        if (device.readState() != 1) return Result.FAILED;
        if (!device.isDeckscapeActive()) {
            // A user may switch wallpapers while ADB is connecting. Do not lock out BYD.
            return restore(device, journal) == Result.APPLIED ? Result.NOT_ACTIVE : Result.FAILED;
        }
        return Result.APPLIED;
    }

    static Result restore(Device device, Journal journal) throws Exception {
        int previous = journal.previousState();
        // Explicit recovery also works after app data was cleared or an older build was used.
        int target = previous == -1 ? 0 : previous;
        if (target < 0 || target > 4 || target == 1) return Result.FAILED;
        int state = device.readState();
        if (state < 0 || state > 4) return Result.FAILED;
        if (state != target) device.setState(target);
        device.persistState();
        if (device.readState() != target) return Result.FAILED;
        return journal.clear() ? Result.APPLIED : Result.FAILED;
    }
}
