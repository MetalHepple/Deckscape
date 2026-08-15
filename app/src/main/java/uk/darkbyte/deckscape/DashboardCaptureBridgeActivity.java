package uk.darkbyte.deckscape;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Starts capture from a fresh foreground activity before opening Home.
 *
 * <p>Android 10 permits a process to restore its task for a short period after one of its
 * activities launches. Starting this transparent bridge after projection consent makes that
 * period cover the one-frame capture instead of depending on when Deckscape was first opened.</p>
 */
public final class DashboardCaptureBridgeActivity extends Activity {
    private static final long OPEN_HOME_DELAY_MILLIS = 700L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean initialResumeSeen;
    private boolean homeOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent source = getIntent();
        int resultCode = source == null ? Integer.MIN_VALUE : source.getIntExtra(
                DashboardCaptureService.EXTRA_RESULT_CODE, Integer.MIN_VALUE);
        Intent resultData = source == null ? null : DashboardCaptureService.resultIntent(source);
        if (resultCode == Integer.MIN_VALUE || resultData == null) {
            abortCapture("Android did not provide screen-capture permission");
            return;
        }

        try {
            Intent service = new Intent(this, DashboardCaptureService.class)
                    .putExtra(DashboardCaptureService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(DashboardCaptureService.EXTRA_RESULT_DATA, resultData)
                    .putExtra(DashboardCaptureService.EXTRA_RETURN_TASK_ID, getTaskId());
            startForegroundService(service);
            mainHandler.postDelayed(this::openHomeForCapture, OPEN_HOME_DELAY_MILLIS);
        } catch (RuntimeException exception) {
            abortCapture("Dashboard capture could not start");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!initialResumeSeen) {
            initialResumeSeen = true;
            return;
        }
        if (homeOpened && !DashboardCaptureStore.isPending(this)) finish();
    }

    private void openHomeForCapture() {
        if (!DashboardCaptureStore.isPending(this)) {
            finish();
            return;
        }
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            homeOpened = true;
            startActivity(home);
        } catch (RuntimeException exception) {
            homeOpened = false;
            stopService(new Intent(this, DashboardCaptureService.class));
            abortCapture("Home screen could not be opened");
        }
    }

    private void abortCapture(String message) {
        mainHandler.removeCallbacksAndMessages(null);
        DashboardCaptureStore.restoreWidgets(this);
        Toast.makeText(this, message + ". Wallpaper widgets are visible again.",
                Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
