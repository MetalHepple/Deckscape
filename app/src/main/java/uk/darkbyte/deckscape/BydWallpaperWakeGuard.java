package uk.darkbyte.deckscape;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reapplies the fixed BYD repair during the short period after the wallpaper wakes. */
final class BydWallpaperWakeGuard implements AutoCloseable {
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean repairInFlight;
    private boolean closed;

    BydWallpaperWakeGuard(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized void onWallpaperVisible(boolean preview) {
        if (!shouldStart(BydWallpaperProtectionPolicy.isAvailable(), true, preview,
                repairInFlight, closed)) {
            return;
        }
        repairInFlight = true;
        executor.execute(() -> {
            try {
                BydWallpaperProtection.guardWakeWindow(context);
            } finally {
                synchronized (BydWallpaperWakeGuard.this) {
                    repairInFlight = false;
                }
            }
        });
    }

    static boolean shouldStart(boolean hardwareEligible, boolean visible, boolean preview,
                               boolean alreadyRunning, boolean closed) {
        // Coalesce callbacks during the bounded window; the next wake can start
        // a new window as soon as this one ends.
        return hardwareEligible && visible && !preview && !alreadyRunning && !closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        executor.shutdownNow();
    }
}
