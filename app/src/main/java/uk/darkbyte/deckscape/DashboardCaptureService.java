package uk.darkbyte.deckscape;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Captures one user-consented home-screen frame while wallpaper cards are hidden. */
public final class DashboardCaptureService extends Service {
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";
    static final String EXTRA_RETURN_TASK_ID = "return_task_id";
    static final String ACTION_OPEN_EDITOR =
            "uk.darkbyte.deckscape.action.OPEN_DASHBOARD_EDITOR";

    private static final String CHANNEL_ID = "dashboard_capture";
    private static final int NOTIFICATION_ID = 52;
    private static final long CAPTURE_FRAME_MILLIS = 3_000L;
    private static final long CAPTURE_TIMEOUT_MILLIS = 12_000L;

    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private boolean finished;
    private long captureAtUptime;
    private int returnTaskId = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        captureThread = new HandlerThread("DeckscapeDashboardCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startCaptureForeground();
        if (intent == null || finished) {
            fail("Dashboard capture could not start");
            return START_NOT_STICKY;
        }
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Integer.MIN_VALUE);
        Intent resultData = resultIntent(intent);
        returnTaskId = intent.getIntExtra(EXTRA_RETURN_TASK_ID, -1);
        if (resultCode == Integer.MIN_VALUE || resultData == null) {
            fail("Android did not provide screen-capture permission");
            return START_NOT_STICKY;
        }
        DashboardCaptureStore.disableWidgets(this);
        captureHandler.post(() -> startProjection(resultCode, resultData));
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("deprecation")
    static Intent resultIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    @SuppressWarnings("deprecation")
    private void startProjection(int resultCode, Intent resultData) {
        if (finished) return;
        try {
            MediaProjectionManager manager = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) throw new IllegalStateException(
                    "Screen capture is unavailable on this device");
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException(
                    "Android declined screen-capture permission");
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    if (!finished) fail("Android stopped dashboard capture");
                }
            };
            projection.registerCallback(projectionCallback, captureHandler);

            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) throw new IllegalStateException(
                    "Display information is unavailable");
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            if (!DashboardCapturePolicy.validDimensions(width, height)) {
                throw new IllegalStateException("Display dimensions are invalid");
            }
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            captureAtUptime = SystemClock.uptimeMillis() + CAPTURE_FRAME_MILLIS;
            imageReader.setOnImageAvailableListener(reader -> {
                if (finished) return;
                if (SystemClock.uptimeMillis() >= captureAtUptime) {
                    captureLatestFrame();
                } else {
                    Image image = reader.acquireLatestImage();
                    if (image != null) image.close();
                }
            }, captureHandler);
            virtualDisplay = projection.createVirtualDisplay(
                    "Deckscape dashboard reference", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, captureHandler);
            captureHandler.postDelayed(this::captureLatestFrame,
                    CAPTURE_FRAME_MILLIS + 300L);
            captureHandler.postDelayed(() -> fail("Dashboard capture timed out"),
                    CAPTURE_TIMEOUT_MILLIS);
        } catch (Exception exception) {
            fail(exception.getMessage() == null ? "Dashboard capture failed"
                    : exception.getMessage());
        }
    }

    private void captureLatestFrame() {
        if (finished || imageReader == null) return;
        Image image = null;
        Bitmap bitmap = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                captureHandler.postDelayed(this::captureLatestFrame, 300L);
                return;
            }
            bitmap = toBitmap(image);
            DashboardCaptureStore.saveReference(this, bitmap, System.currentTimeMillis());
            finishCapture(true, "Dashboard captured");
        } catch (Exception exception) {
            fail(exception.getMessage() == null ? "Dashboard capture failed"
                    : exception.getMessage());
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (image != null) image.close();
        }
    }

    private static Bitmap toBitmap(Image image) throws IOException {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new IOException("Dashboard capture contained no image plane");
        }
        Image.Plane plane = planes[0];
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        if (pixelStride <= 0 || rowStride <= 0) {
            throw new IOException("Dashboard capture pixel layout is invalid");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int paddedWidth = width + Math.max(0, rowStride - pixelStride * width) / pixelStride;
        if (!DashboardCapturePolicy.validDimensions(paddedWidth, height)) {
            throw new IOException("Dashboard capture buffer dimensions are invalid");
        }
        ByteBuffer buffer = plane.getBuffer();
        Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == width) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private void fail(String message) {
        finishCapture(false, message == null ? "Dashboard capture failed" : message);
    }

    private synchronized void finishCapture(boolean success, String message) {
        if (finished) return;
        finished = true;
        DashboardCaptureStore.restoreWidgets(this);
        releaseProjection();
        stopForeground(true);
        showCompletionNotification(success, message);
        returnToDeckscapeTask();
        new Handler(getMainLooper()).post(() -> Toast.makeText(this,
                success ? "Dashboard captured."
                        : "Dashboard capture failed. Wallpaper widgets are visible again.",
                Toast.LENGTH_LONG).show());
        stopSelf();
    }

    private void returnToDeckscapeTask() {
        if (returnTaskId < 0) return;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return;
        try {
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                int taskId = info == null ? -1 : Build.VERSION.SDK_INT >= 29
                        ? info.taskId : info.id;
                if (taskId == returnTaskId) {
                    task.moveToFront();
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // The completion notification remains available if the task cannot be restored.
        }
    }

    private void releaseProjection() {
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (projection != null) {
            if (projectionCallback != null) projection.unregisterCallback(projectionCallback);
            projection.stop();
            projection = null;
        }
        projectionCallback = null;
    }

    private void startCaptureForeground() {
        Notification notification = notification("Capturing dashboard",
                "Wallpaper widgets will return when capture finishes", false);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void showCompletionNotification(boolean success, String message) {
        NotificationManager manager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        manager.notify(NOTIFICATION_ID, notification(
                success ? "Dashboard captured" : "Dashboard capture failed",
                success ? "Tap to arrange wallpaper widgets" : "Tap to return to Deckscape",
                true));
    }

    static void dismissCompletionNotification(Context context) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private Notification notification(String title, String text, boolean opensEditor) {
        Intent activityIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (opensEditor) activityIntent.setAction(ACTION_OPEN_EDITOR);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        return builder
                .setSmallIcon(R.drawable.ic_dashboard_capture)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(!opensEditor)
                .setAutoCancel(opensEditor)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Dashboard capture", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shown while Deckscape captures Home for widget placement");
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (!finished) {
            finished = true;
            DashboardCaptureStore.restoreWidgets(this);
            releaseProjection();
        }
        if (captureThread != null) captureThread.quitSafely();
        super.onDestroy();
    }
}
