package uk.darkbyte.deckscape;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Landscape preview gallery for the current visible wallpaper results. */
final class WallpaperPreviewDialog {
    /** Receives explicit original-file actions without coupling preview rendering to storage. */
    interface Listener {
        void onGet(CatalogItem item, ActionCallback callback);

        void onSet(CatalogItem item, ActionCallback callback);
    }

    /** Reports one asynchronous preview action back to its currently visible controls. */
    interface ActionCallback {
        void onProgress(int percent);

        void onComplete(boolean success);
    }

    private final Activity activity;
    private final PreviewCache previews;
    private final RepositorySource source;
    private final PreviewSequence sequence;
    private final Listener listener;

    private AlertDialog dialog;
    private TextView title;
    private TextView description;
    private TextView loading;
    private TextView position;
    private ImageView image;
    private AnimatedGifView animation;
    private Button previous;
    private Button next;
    private Button get;
    private Button set;
    private int requestGeneration;

    WallpaperPreviewDialog(Activity activity, PreviewCache previews,
                           RepositorySource source, PreviewSequence sequence,
                           Listener listener) {
        this.activity = activity;
        this.previews = previews;
        this.source = source;
        this.sequence = sequence;
        this.listener = listener;
    }

    /** Opens the gallery when its snapshot contains at least one wallpaper. */
    void show() {
        if (sequence.isEmpty()) return;

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(activity, 22), Ui.dp(activity, 18),
                Ui.dp(activity, 22), Ui.dp(activity, 12));

        title = Ui.title(activity, "", 20);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 38)));

        description = Ui.text(activity, "", 12, Ui.MUTED);
        panel.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 30)));

        FrameLayout previewFrame = buildPreviewFrame();
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 420));
        frameParams.topMargin = Ui.dp(activity, 8);
        panel.addView(previewFrame, frameParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        position = Ui.title(activity, "", 12);
        position.setTextColor(Ui.MUTED);
        actions.addView(position, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        get = Ui.button(activity, "Get", true);
        LinearLayout.LayoutParams getParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 112), Ui.dp(activity, 46));
        getParams.rightMargin = Ui.dp(activity, 8);
        actions.addView(get, getParams);

        set = Ui.button(activity, "Set", false);
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 112), Ui.dp(activity, 46));
        setParams.rightMargin = Ui.dp(activity, 8);
        actions.addView(set, setParams);

        Button close = Ui.button(activity, "Close", false);
        actions.addView(close, new LinearLayout.LayoutParams(
                Ui.dp(activity, 104), Ui.dp(activity, 46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 58)));

        dialog = new AlertDialog.Builder(activity).setView(panel).create();
        close.setOnClickListener(view -> dialog.dismiss());
        get.setOnClickListener(view -> getCurrent());
        set.setOnClickListener(view -> setCurrent());
        previous.setOnClickListener(view -> {
            sequence.previous();
            renderCurrent();
        });
        next.setOnClickListener(view -> {
            sequence.next();
            renderCurrent();
        });
        dialog.setOnShowListener(ignored -> styleDialog());
        dialog.setOnDismissListener(ignored -> {
            requestGeneration++;
            animation.clearMovie();
        });
        dialog.show();
        renderCurrent();
    }

    private FrameLayout buildPreviewFrame() {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(activity, 12),
                Ui.DIVIDER, Ui.dp(activity, 1)));
        frame.setClipToOutline(true);

        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        animation = new AnimatedGifView(activity);
        animation.setVisibility(View.GONE);
        frame.addView(animation, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loading = Ui.title(activity, "LOADING PREVIEW", 12);
        loading.setTextColor(Ui.MUTED);
        loading.setGravity(Gravity.CENTER);
        frame.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        previous = navigationButton("‹", "Previous wallpaper");
        FrameLayout.LayoutParams previousParams = new FrameLayout.LayoutParams(
                Ui.dp(activity, 58), Ui.dp(activity, 92), Gravity.START | Gravity.CENTER_VERTICAL);
        previousParams.leftMargin = Ui.dp(activity, 12);
        frame.addView(previous, previousParams);

        next = navigationButton("›", "Next wallpaper");
        FrameLayout.LayoutParams nextParams = new FrameLayout.LayoutParams(
                Ui.dp(activity, 58), Ui.dp(activity, 92), Gravity.END | Gravity.CENTER_VERTICAL);
        nextParams.rightMargin = Ui.dp(activity, 12);
        frame.addView(next, nextParams);
        return frame;
    }

    private Button navigationButton(String label, String contentDescription) {
        Button button = Ui.button(activity, label, false);
        button.setTextSize(34);
        button.setPadding(0, 0, 0, Ui.dp(activity, 4));
        button.setContentDescription(contentDescription);
        button.setBackground(Ui.rounded(Color.argb(232, 20, 38, 56),
                Ui.dp(activity, 18), Ui.DIVIDER, Ui.dp(activity, 1)));
        return button;
    }

    private void renderCurrent() {
        CatalogItem item = sequence.current();
        if (item == null || dialog == null || !dialog.isShowing()) return;
        int generation = ++requestGeneration;
        String displayName = WallpaperStore.displayName(item.name);

        title.setText(displayName);
        description.setText(item.isGif()
                ? "Animated preview • original GIF is cached temporarily for playback"
                : "Optimised 16:9 preview • choose Get to save the original");
        refreshActions(item);
        position.setText(activity.getString(R.string.preview_position,
                sequence.position(), sequence.size()));
        previous.setEnabled(sequence.hasPrevious());
        previous.setAlpha(sequence.hasPrevious() ? 1f : 0.35f);
        next.setEnabled(sequence.hasNext());
        next.setAlpha(sequence.hasNext() ? 1f : 0.35f);
        int navigationVisibility = sequence.size() > 1 ? View.VISIBLE : View.GONE;
        previous.setVisibility(navigationVisibility);
        next.setVisibility(navigationVisibility);

        image.setImageDrawable(null);
        image.setVisibility(item.isGif() ? View.GONE : View.VISIBLE);
        image.setContentDescription("Preview of " + displayName);
        animation.clearMovie();
        animation.setVisibility(item.isGif() ? View.VISIBLE : View.GONE);
        animation.setContentDescription("Animated preview of " + displayName);
        loading.setText(R.string.loading_preview);
        loading.setVisibility(View.VISIBLE);

        if (item.isGif()) {
            previews.requestGif(source, item, (movie, error) -> {
                if (!isCurrent(generation)) return;
                if (movie != null) {
                    animation.setMovie(movie);
                    loading.setVisibility(View.GONE);
                } else {
                    loading.setText(error == null ? "GIF PREVIEW UNAVAILABLE" : error);
                }
            });
        } else {
            previews.request(source, item, (bitmap, error) -> {
                if (!isCurrent(generation)) return;
                if (bitmap != null) {
                    image.setImageBitmap(bitmap);
                    loading.setVisibility(View.GONE);
                } else {
                    loading.setText(error == null ? "PREVIEW UNAVAILABLE" : error);
                }
            });
        }
    }

    private boolean isCurrent(int generation) {
        return generation == requestGeneration && dialog != null && dialog.isShowing();
    }

    private void getCurrent() {
        CatalogItem item = sequence.current();
        if (item == null || listener == null) return;
        int generation = requestGeneration;
        get.setEnabled(false);
        get.setText(R.string.preview_getting);
        set.setEnabled(false);
        listener.onGet(item, new ActionCallback() {
            @Override
            public void onProgress(int percent) {
                if (!isCurrent(generation)) return;
                get.setText(percent > 0
                        ? activity.getString(R.string.preview_get_progress, percent)
                        : activity.getString(R.string.preview_getting));
            }

            @Override
            public void onComplete(boolean success) {
                if (!isCurrent(generation)) return;
                refreshActions(item);
            }
        });
    }

    private void setCurrent() {
        CatalogItem item = sequence.current();
        if (item == null || listener == null) return;
        int generation = requestGeneration;
        get.setEnabled(false);
        set.setEnabled(false);
        set.setText(R.string.preview_setting);
        listener.onSet(item, new ActionCallback() {
            @Override
            public void onProgress(int percent) {
                // Setting an on-device file has no meaningful byte progress.
            }

            @Override
            public void onComplete(boolean success) {
                if (!isCurrent(generation)) return;
                refreshActions(item);
            }
        });
    }

    private void refreshActions(CatalogItem item) {
        boolean installed = WallpaperStore.installedFile(activity, source, item) != null;
        get.setText(installed ? "On device" : "Get");
        get.setEnabled(!installed && WallpaperRules.canInstall(item));
        get.setAlpha(get.isEnabled() ? 1f : 0.55f);
        set.setText(R.string.action_set);
        set.setEnabled(installed);
        set.setAlpha(installed ? 1f : 0.55f);
    }

    private void styleDialog() {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(activity, 18),
                Ui.DIVIDER, Ui.dp(activity, 1)));
        int available = activity.getResources().getDisplayMetrics().widthPixels
                - Ui.dp(activity, 48);
        window.setLayout(Math.min(available, Ui.dp(activity, 960)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
