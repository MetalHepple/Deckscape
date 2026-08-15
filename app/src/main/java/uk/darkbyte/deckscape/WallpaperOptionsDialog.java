package uk.darkbyte.deckscape;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;

/** Builds the spacious, head-unit-friendly controls for one downloaded wallpaper. */
final class WallpaperOptionsDialog {
    /** Applies library actions selected from the dialog. */
    interface Listener {
        void onProfileSaved(File file);

        void onSetNow(File file);

        void onDelete(File file);
    }

    private WallpaperOptionsDialog() {}

    static void show(Activity activity, ExecutorService executor, File file, Listener listener) {
        WallpaperProfileStore store = new WallpaperProfileStore(activity);
        DayNightSettings settings = new DayNightSettings(activity);
        WallpaperProfile original = store.get(file);
        WallpaperProfile[] editing = {original};

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(activity, 22), Ui.dp(activity, 16),
                Ui.dp(activity, 22), Ui.dp(activity, 12));

        TextView heading = Ui.title(activity, WallpaperStore.displayName(file), 21);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 40)));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 355)));

        FrameLayout previewFrame = new FrameLayout(activity);
        previewFrame.setBackground(Ui.rounded(Color.BLACK, Ui.dp(activity, 12),
                Ui.DIVIDER, Ui.dp(activity, 1)));
        previewFrame.setClipToOutline(true);
        WallpaperCropView preview = new WallpaperCropView(activity);
        preview.setProfile(original, settings.defaultScaleMode());
        previewFrame.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView loading = Ui.title(activity, "LOADING PREVIEW", 12);
        loading.setGravity(Gravity.CENTER);
        loading.setTextColor(Ui.MUTED);
        previewFrame.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(previewFrame, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.45f));

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(Ui.dp(activity, 18), 0, 0, 0);
        content.addView(controls, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        controls.addView(sectionLabel(activity, "DISPLAY"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 28)));
        Spinner scale = spinner(activity, labels(ScaleMode.values()));
        scale.setSelection(original.scaleMode.ordinal());
        controls.addView(scale, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48)));

        LinearLayout zoomRow = new LinearLayout(activity);
        zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView zoomLabel = Ui.text(activity, zoomLabel(original.zoom), 12, Ui.MUTED);
        SeekBar zoom = new SeekBar(activity);
        zoom.setMax(200);
        zoom.setProgress(Math.round((original.zoom - 1f) * 100));
        zoomRow.addView(zoomLabel, new LinearLayout.LayoutParams(
                Ui.dp(activity, 82), ViewGroup.LayoutParams.MATCH_PARENT));
        zoomRow.addView(zoom, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        Button reset = Ui.actionButton(activity, "Reset", false);
        reset.setSingleLine(true);
        zoomRow.addView(reset, new LinearLayout.LayoutParams(
                Ui.dp(activity, 76), Ui.dp(activity, 44)));
        controls.addView(zoomRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 54)));

        TextView cropHelp = Ui.text(activity,
                "Drag the preview to position the crop.", 11, Ui.MUTED);
        cropHelp.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(cropHelp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 30)));

        Spinner[] roleHolder = {null};
        if (settings.isEnabled()) {
            controls.addView(sectionLabel(activity, "DAY & NIGHT"),
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 28)));
            if (settings.assignmentMode() == DayNightAssignmentMode.AUTO) {
                TextView automatic = Ui.text(activity,
                        "Automatically sorted as " + settings.effectiveRole(file).label
                                + " from image brightness.", 13, Ui.MUTED);
                automatic.setGravity(Gravity.CENTER_VERTICAL);
                controls.addView(automatic, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48)));
            } else {
                roleHolder[0] = spinner(activity, labels(DayNightRole.values()));
                roleHolder[0].setSelection(original.role.ordinal());
                controls.addView(roleHolder[0], new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48)));
            }
        }

        scale.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ScaleMode selected = ScaleMode.values()[position];
                editing[0] = editing[0].withScaleMode(selected);
                preview.setScaleMode(selected);
                boolean custom = selected == ScaleMode.CUSTOM;
                zoomRow.setVisibility(custom ? View.VISIBLE : View.GONE);
                cropHelp.setVisibility(custom ? View.VISIBLE : View.GONE);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        Spinner role = roleHolder[0];
        if (role != null) {
            role.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view,
                                           int position, long id) {
                    editing[0] = editing[0].withRole(DayNightRole.values()[position]);
                }

                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1f + progress / 100f;
                zoomLabel.setText(zoomLabel(value));
                preview.setZoom(value);
                editing[0] = editing[0].withCrop(value, preview.focusX(), preview.focusY());
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        reset.setOnClickListener(view -> {
            zoom.setProgress(0);
            preview.resetCrop();
            editing[0] = editing[0].withCrop(1f, 0.5f, 0.5f);
        });
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button delete = Ui.actionButton(activity, "Delete", false);
        delete.setTextColor(Ui.CORAL);
        delete.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(activity, 10),
                Ui.CORAL, Ui.dp(activity, 1)));
        Button show = Ui.actionButton(activity, "Set", false);
        Button cancel = Ui.actionButton(activity, "Cancel", false);
        Button save = Ui.button(activity, "Save options", true);
        addAction(activity, actions, delete, false);
        addAction(activity, actions, show, true);
        addAction(activity, actions, cancel, true);
        addAction(activity, actions, save, true);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 58)));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(panel).create();
        boolean[] dismissed = {false};
        save.setOnClickListener(view -> {
            WallpaperProfile value = editing[0].withCrop(
                    1f + zoom.getProgress() / 100f, preview.focusX(), preview.focusY());
            store.put(file, value);
            listener.onProfileSaved(file);
            dialog.dismiss();
        });
        show.setOnClickListener(view -> {
            listener.onSetNow(file);
            dialog.dismiss();
        });
        cancel.setOnClickListener(view -> dialog.dismiss());
        delete.setOnClickListener(view -> {
            AlertDialog confirmation = new AlertDialog.Builder(activity)
                    .setTitle("Delete " + WallpaperStore.displayName(file) + "?")
                    .setMessage("This removes the downloaded wallpaper from this device and "
                            + "the slideshow. You can download it again from its source.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (ignored, which) -> {
                        listener.onDelete(file);
                        dialog.dismiss();
                    })
                    .create();
            confirmation.setOnShowListener(ignored -> style(activity, confirmation, 540));
            confirmation.show();
        });
        dialog.setOnDismissListener(ignored -> {
            dismissed[0] = true;
            preview.release();
        });
        dialog.setOnShowListener(ignored -> style(activity, dialog, 1_050));
        dialog.show();

        executor.execute(() -> {
            try {
                WallpaperPreviewDecoder.Decoded decoded = WallpaperPreviewDecoder.decode(file);
                activity.runOnUiThread(() -> {
                    if (dismissed[0] || !dialog.isShowing()) {
                        if (decoded.bitmap != null && !decoded.bitmap.isRecycled()) {
                            decoded.bitmap.recycle();
                        }
                        return;
                    }
                    preview.setDecoded(decoded);
                    loading.setVisibility(View.GONE);
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    if (!dismissed[0]) loading.setText(R.string.preview_unavailable);
                });
            }
        });
    }

    private static TextView sectionLabel(Activity activity, String text) {
        TextView label = Ui.title(activity, text, 11);
        label.setTextColor(Ui.CYAN);
        return label;
    }

    private static Spinner spinner(Activity activity, String[] values) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(activity, 10),
                Ui.DIVIDER, Ui.dp(activity, 1)));
        return spinner;
    }

    private static String[] labels(ScaleMode[] values) {
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) labels[index] = values[index].label;
        return labels;
    }

    private static String[] labels(DayNightRole[] values) {
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) labels[index] = values[index].label;
        return labels;
    }

    private static String zoomLabel(float value) {
        return String.format(java.util.Locale.ROOT, "ZOOM %.1fx", value);
    }

    private static void addAction(Activity activity, LinearLayout row, Button button,
                                  boolean margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Ui.dp(activity, 46), 1f);
        if (margin) params.leftMargin = Ui.dp(activity, 8);
        row.addView(button, params);
    }

    private static void style(Activity activity, AlertDialog dialog, int widthDp) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(activity, 18),
                Ui.DIVIDER, Ui.dp(activity, 1)));
        int available = activity.getResources().getDisplayMetrics().widthPixels
                - Ui.dp(activity, 48);
        window.setLayout(Math.min(available, Ui.dp(activity, widthDp)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) positive.setTextColor(Ui.CORAL);
        if (negative != null) negative.setTextColor(Ui.MUTED);
    }
}
