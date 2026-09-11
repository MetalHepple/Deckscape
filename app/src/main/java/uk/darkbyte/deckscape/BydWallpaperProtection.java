package uk.darkbyte.deckscape;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import dadb.AdbKeyPair;
import dadb.AdbShellResponse;
import dadb.Dadb;

/** Applies the narrow, local-only BYD wallpaper persistence repair. */
final class BydWallpaperProtection {
    private static final String TAG = "DeckscapeBydRepair";
    private static final String KEY_DIRECTORY = "byd-wallpaper-protection";
    private static final String PRIVATE_KEY = "adbkey";
    private static final String PUBLIC_KEY = "adbkey.pub";
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final int ADB_PORT = 5555;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = 5_000;
    private static final int MAX_CONNECTION_ATTEMPTS = 12;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final String PREFS = "byd_persistent_protection";
    private static final String APPROVED = "approved";
    private static final String PREVIOUS_STATE = "previous_wallpaper_appop";
    private static final String LEGACY_PREVIOUS_STATE = "previous_enabled_state";
    private static volatile boolean verifiedThisProcess;

    enum Result {
        APPLIED,
        NOT_ACTIVE,
        NOT_APPLICABLE,
        FAILED
    }

    private BydWallpaperProtection() {}

    static boolean hasDecision(Context context) {
        return preferences(context).contains(APPROVED);
    }

    static boolean isApproved(Context context) {
        return preferences(context).getBoolean(APPROVED, false);
    }

    static boolean setApproved(Context context, boolean approved) {
        if (!approved) verifiedThisProcess = false;
        return preferences(context).edit().putBoolean(APPROVED, approved).commit();
    }

    static boolean isProtected(Context context) {
        if (!BydWallpaperProtectionPolicy.isAvailable() || !isApproved(context)
                || !verifiedThisProcess) return false;
        try {
            int state = context.getPackageManager().getApplicationEnabledSetting(
                    BydWallpaperProtectionPolicy.PACKAGE_NAME);
            return state == 0 || state == 1;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized Result apply(Context context) {
        if (!isApproved(context)) return Result.NOT_APPLICABLE;
        return change(context, false);
    }

    static synchronized Result restore(Context context) {
        if (!setApproved(context, false)) return Result.FAILED;
        return change(context, true);
    }

    private static Result change(Context context, boolean restore) {
        verifiedThisProcess = false;
        Context applicationContext = context.getApplicationContext();
        if (!BydWallpaperProtectionPolicy.isAvailable()) {
            return Result.NOT_APPLICABLE;
        }

        try {
            AdbKeyPair keyPair = loadOrCreateKeyPair(applicationContext);
            Log.i(TAG, restore ? "Restoring BYD wallpaper permission" : "Applying wallpaper permission protection");
            for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS
                    && !Thread.currentThread().isInterrupted(); attempt++) {
                try (Dadb adb = Dadb.create(LOOPBACK_HOST, ADB_PORT, keyPair,
                        CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS)) {
                    if (Thread.currentThread().isInterrupted()) break;
                    AdbShellResponse identity = adb.shell(BydWallpaperProtectionPolicy.IDENTITY_COMMAND);
                    if (identity.getExitCode() != 0
                            || !BydWallpaperProtectionPolicy.identityMatches(identity.getOutput())) {
                        return Result.FAILED;
                    }
                    BydWallpaperProtectionTransaction.Device device = device(applicationContext, adb);
                    BydWallpaperProtectionTransaction.Journal journal = journal(applicationContext, PREVIOUS_STATE);
                    BydWallpaperProtectionTransaction.Result result = restore
                            ? BydWallpaperProtectionTransaction.restore(device, journal)
                            : BydWallpaperProtectionTransaction.apply(device, journal);
                    if (result == BydWallpaperProtectionTransaction.Result.NOT_ACTIVE) return Result.NOT_ACTIVE;
                    if (result == BydWallpaperProtectionTransaction.Result.FAILED) return Result.FAILED;
                    // Protect first, then recover any owned legacy package disable. The provider
                    // can return without opening an unprotected interval for BYD takeover.
                    if (!recoverLegacyPackage(applicationContext, adb)) return Result.FAILED;
                    AdbShellResponse packageState = adb.shell(BydWallpaperProtectionPolicy.PACKAGE_STATE_COMMAND);
                    int enabled = BydWallpaperProtectionPolicy.packageState(packageState.getExitCode(), packageState.getOutput());
                    if (enabled != 0 && enabled != 1) return Result.FAILED;
                    if (!restore && !device.isDeckscapeActive()) {
                        return BydWallpaperProtectionTransaction.restore(device, journal)
                                == BydWallpaperProtectionTransaction.Result.APPLIED
                                ? Result.NOT_ACTIVE : Result.FAILED;
                    }
                    verifiedThisProcess = !restore && isApproved(applicationContext);
                    Log.i(TAG, restore ? "BYD wallpaper permission restored" : "Wallpaper permission protection verified");
                    return Result.APPLIED;
                } catch (PackageChangeRejectedException exception) {
                    return Result.FAILED;
                } catch (Exception exception) {
                    // The first connection can end while Android records the user's ADB
                    // approval. Reconnect with the same key instead of showing another prompt.
                }
                if (attempt + 1 < MAX_CONNECTION_ATTEMPTS && !waitFor(RETRY_DELAY_MS)) {
                    break;
                }
            }
        } catch (Exception exception) {
            // Result-only logging deliberately excludes keys, commands, and shell output.
        }
        Log.w(TAG, "BYD wallpaper persistence repair failed");
        return Result.FAILED;
    }

    private static BydWallpaperProtectionTransaction.Device device(Context context, Dadb adb) {
        return new BydWallpaperProtectionTransaction.Device() {
            @Override public boolean isDeckscapeActive() {
                WallpaperInfo info = WallpaperManager.getInstance(context).getWallpaperInfo();
                return isApproved(context) && !Thread.currentThread().isInterrupted()
                        && info != null && new ComponentName(context, WallpaperEngineService.class)
                                .equals(info.getComponent());
            }

            @Override public int readState() throws Exception {
                AdbShellResponse response = adb.shell(BydWallpaperProtectionPolicy.VERIFY_COMMAND);
                return BydWallpaperProtectionPolicy.operationState(response.getExitCode(), response.getOutput());
            }

            @Override public void setState(int state) throws Exception {
                String command = BydWallpaperProtectionPolicy.operationCommand(state);
                if (adb.shell(command).getExitCode() != 0) throw new PackageChangeRejectedException();
            }

            @Override public void persistState() throws Exception {
                // Android batches AppOps disk writes. Flush before reporting reboot protection.
                if (adb.shell(BydWallpaperProtectionPolicy.PERSIST_COMMAND).getExitCode() != 0) {
                    throw new PackageChangeRejectedException();
                }
            }
        };
    }

    private static boolean recoverLegacyPackage(Context context, Dadb adb) throws Exception {
        return BydWallpaperLegacyRecovery.restore(new BydWallpaperLegacyRecovery.Device() {
            @Override public int readState() throws Exception {
                AdbShellResponse response = adb.shell(BydWallpaperProtectionPolicy.PACKAGE_STATE_COMMAND);
                return BydWallpaperProtectionPolicy.packageState(response.getExitCode(), response.getOutput());
            }
            @Override public void restoreState(int state) throws Exception {
                if (adb.shell(BydWallpaperProtectionPolicy.legacyRestoreCommand(state)).getExitCode() != 0) {
                    throw new PackageChangeRejectedException();
                }
            }
        }, journal(context, LEGACY_PREVIOUS_STATE));
    }

    private static BydWallpaperProtectionTransaction.Journal journal(Context context, String key) {
        SharedPreferences prefs = preferences(context);
        return new BydWallpaperProtectionTransaction.Journal() {
            @Override public int previousState() { return prefs.getInt(key, -1); }
            @Override public boolean save(int state) {
                return prefs.edit().putInt(key, state).commit();
            }
            @Override public boolean clear() {
                return prefs.edit().remove(key).commit();
            }
        };
    }

    private static final class PackageChangeRejectedException extends IOException {
        PackageChangeRejectedException() { super("Package change rejected"); }
    }

    private static boolean waitFor(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static synchronized AdbKeyPair loadOrCreateKeyPair(Context context)
            throws IOException {
        File directory = new File(context.getNoBackupFilesDir(), KEY_DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create private key directory");
        }
        File privateKey = new File(directory, PRIVATE_KEY);
        File publicKey = new File(directory, PUBLIC_KEY);
        if (privateKey.isFile() && publicKey.isFile()) {
            try {
                return AdbKeyPair.read(privateKey, publicKey);
            } catch (RuntimeException exception) {
                deleteKeyFile(privateKey);
                deleteKeyFile(publicKey);
            }
        } else {
            deleteKeyFile(privateKey);
            deleteKeyFile(publicKey);
        }
        AdbKeyPair.generate(privateKey, publicKey);
        return AdbKeyPair.read(privateKey, publicKey);
    }

    private static void deleteKeyFile(File file) throws IOException {
        if (file.exists() && (!file.isFile() || !file.delete())) {
            throw new IOException("Could not replace private key file");
        }
    }
}
