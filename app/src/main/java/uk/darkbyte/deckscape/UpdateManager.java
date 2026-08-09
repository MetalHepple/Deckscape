package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Coordinates daily release checks, automatic downloads, verification, and UI state. */
final class UpdateManager {
    enum Phase { IDLE, CHECKING, UP_TO_DATE, DOWNLOADING, READY, ERROR }

    /** Immutable snapshot safe for rendering from the main thread. */
    static final class State {
        final Phase phase;
        final UpdateRelease release;
        final int progress;
        final String message;
        final File file;

        State(Phase phase, UpdateRelease release, int progress, String message, File file) {
            this.phase = phase;
            this.release = release;
            this.progress = progress;
            this.message = message;
            this.file = file;
        }

        boolean canInstall() {
            return phase == Phase.READY && file != null && file.isFile();
        }
    }

    interface Listener {
        void onUpdateStateChanged(State state);
    }

    private static final String PREFS = "app_updates";
    private static final String KEY_LAST_CHECK = "last_successful_check";
    private static final String KEY_VERSION = "ready_version";
    private static final String KEY_TITLE = "ready_title";
    private static final String KEY_NOTES = "ready_notes";
    private static final String KEY_PAGE = "ready_page";
    private static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1_000L;

    private final Context context;
    private final UpdateClient client;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Object lock = new Object();

    private volatile State state = new State(Phase.IDLE, null, 0,
            "Updates are checked automatically.", null);
    private boolean running;
    private boolean closed;

    UpdateManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        client = new UpdateClient(context);
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    State state() {
        return state;
    }

    /** Restores a verified cached update, otherwise performs the daily GitHub check. */
    void start() {
        launch(false);
    }

    /** Performs an immediate check regardless of the daily throttle. */
    void checkNow() {
        launch(true);
    }

    void close() {
        synchronized (lock) {
            closed = true;
        }
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private void launch(boolean forced) {
        synchronized (lock) {
            if (closed || running) return;
            running = true;
        }
        try {
            executor.execute(() -> {
                try {
                    if (!forced && restoreCachedUpdate()) return;
                    long lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L);
                    if (!forced && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                        publish(new State(Phase.UP_TO_DATE, null, 0,
                                "Deckscape is up to date • checked recently", null));
                        return;
                    }
                    checkAndDownload();
                } finally {
                    synchronized (lock) {
                        running = false;
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            synchronized (lock) {
                running = false;
            }
        }
    }

    private boolean restoreCachedUpdate() {
        File file = client.updateFile();
        String version = preferences.getString(KEY_VERSION, "");
        if (!file.isFile() || version == null || version.isEmpty()) return false;
        try {
            UpdateVerifier.verify(context, file, version);
            UpdateRelease release = UpdateRelease.restored(version,
                    preferences.getString(KEY_TITLE, "Deckscape " + version),
                    preferences.getString(KEY_NOTES, ""),
                    preferences.getString(KEY_PAGE,
                            "https://github.com/MetalHepple/Deckscape/releases/latest"));
            publish(new State(Phase.READY, release, 100,
                    "Deckscape " + version + " is downloaded and verified", file));
            return true;
        } catch (Exception exception) {
            file.delete();
            clearReadyMetadata();
            return false;
        }
    }

    private void checkAndDownload() {
        publish(new State(Phase.CHECKING, null, 0,
                "Checking GitHub for updates…", null));
        UpdateRelease release = null;
        try {
            release = client.latest();
            preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
            if (UpdateVersion.compare(release.versionName, BuildConfig.VERSION_NAME) <= 0) {
                client.updateFile().delete();
                clearReadyMetadata();
                publish(new State(Phase.UP_TO_DATE, null, 0,
                        "Deckscape " + BuildConfig.VERSION_NAME + " is up to date", null));
                return;
            }
            UpdateRelease downloadRelease = release;
            publish(new State(Phase.DOWNLOADING, release, 0,
                    "Downloading Deckscape " + release.versionName + "…", null));
            File file = client.download(release, (downloaded, total) -> {
                int percent = total <= 0 ? 0 : (int) Math.min(100L, downloaded * 100L / total);
                publish(new State(Phase.DOWNLOADING, downloadRelease, percent,
                        "Downloading Deckscape " + downloadRelease.versionName
                                + " • " + percent + "%", null));
            });
            UpdateVerifier.Result verified = UpdateVerifier.verify(
                    context, file, release.versionName);
            remember(release);
            publish(new State(Phase.READY, release, 100,
                    "Deckscape " + verified.versionName + " is downloaded and verified", file));
        } catch (Exception exception) {
            client.updateFile().delete();
            clearReadyMetadata();
            String message = exception.getMessage();
            if (message == null || message.trim().isEmpty()) message = "Unexpected update error";
            publish(new State(Phase.ERROR, release, 0, message, null));
        }
    }

    private void remember(UpdateRelease release) {
        preferences.edit()
                .putString(KEY_VERSION, release.versionName)
                .putString(KEY_TITLE, release.title)
                .putString(KEY_NOTES, release.notes)
                .putString(KEY_PAGE, release.pageUrl)
                .apply();
    }

    private void clearReadyMetadata() {
        preferences.edit()
                .remove(KEY_VERSION)
                .remove(KEY_TITLE)
                .remove(KEY_NOTES)
                .remove(KEY_PAGE)
                .apply();
    }

    private void publish(State value) {
        state = value;
        main.post(() -> {
            synchronized (lock) {
                if (closed) return;
            }
            listener.onUpdateStateChanged(value);
        });
    }
}
