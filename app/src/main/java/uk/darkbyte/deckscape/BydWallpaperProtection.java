package uk.darkbyte.deckscape;

import android.content.Context;
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
    private static final long WAKE_GUARD_DURATION_MS = 45_000L;
    private static final long WAKE_GUARD_POLL_MS = 250L;

    enum Result {
        APPLIED,
        NOT_APPLICABLE,
        FAILED
    }

    private BydWallpaperProtection() {}

    static Result apply(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (!BydWallpaperProtectionPolicy.isAvailable()) {
            return Result.NOT_APPLICABLE;
        }

        try {
            AdbKeyPair keyPair = loadOrCreateKeyPair(applicationContext);
            Log.i(TAG, "Starting BYD wallpaper persistence repair");
            for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS
                    && !Thread.currentThread().isInterrupted(); attempt++) {
                try (Dadb adb = Dadb.create(LOOPBACK_HOST, ADB_PORT, keyPair,
                        CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS)) {
                    if (Thread.currentThread().isInterrupted()) break;
                    AdbShellResponse stop = adb.shell(
                            BydWallpaperProtectionPolicy.FORCE_STOP_COMMAND);
                    AdbShellResponse verify = adb.shell(
                            BydWallpaperProtectionPolicy.VERIFY_STOPPED_COMMAND);
                    if (BydWallpaperProtectionPolicy.repairSucceeded(
                            stop.getExitCode(), verify.getExitCode(), verify.getOutput())) {
                        Log.i(TAG, "BYD wallpaper persistence repair applied");
                        return Result.APPLIED;
                    }
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

    /** Guards the measured BYD boot window without leaving a persistent process behind. */
    static Result guardWakeWindow(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (!BydWallpaperProtectionPolicy.isAvailable()) {
            return Result.NOT_APPLICABLE;
        }

        long deadlineNanos = System.nanoTime() + WAKE_GUARD_DURATION_MS * 1_000_000L;
        boolean guarded = false;
        Log.i(TAG, "Starting BYD wallpaper wake guard");
        try {
            AdbKeyPair keyPair = loadOrCreateKeyPair(applicationContext);
            while (!Thread.currentThread().isInterrupted()
                    && System.nanoTime() < deadlineNanos) {
                try (Dadb adb = Dadb.create(LOOPBACK_HOST, ADB_PORT, keyPair,
                        CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS)) {
                    while (!Thread.currentThread().isInterrupted()
                            && System.nanoTime() < deadlineNanos) {
                        AdbShellResponse response = adb.shell(
                                BydWallpaperProtectionPolicy.STOP_IF_RUNNING_COMMAND);
                        if (response.getExitCode() != 0) break;
                        guarded = true;
                        if (!waitFor(WAKE_GUARD_POLL_MS)) break;
                    }
                } catch (Exception exception) {
                    if (!waitFor(WAKE_GUARD_POLL_MS)) break;
                }
            }
        } catch (Exception exception) {
            // Result-only logging deliberately excludes keys, commands, and shell output.
        }
        if (guarded) {
            Log.i(TAG, "BYD wallpaper wake guard completed");
            return Result.APPLIED;
        }
        Log.w(TAG, "BYD wallpaper wake guard failed");
        return Result.FAILED;
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
