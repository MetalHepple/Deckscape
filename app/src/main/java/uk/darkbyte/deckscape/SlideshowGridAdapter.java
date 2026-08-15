package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Renders every downloaded wallpaper with explicit rotation and deletion controls. */
final class SlideshowGridAdapter extends BaseAdapter {
    /** Receives wallpaper-library actions selected from a card. */
    interface Listener {
        void onSet(File file);

        void onCycleRole(File file, DayNightRole currentRole);

        void onOptions(File file);

        void onDelete(File file);
    }

    private final Context context;
    private final PreviewCache previews;
    private final Listener listener;
    private final DayNightSettings dayNight;
    private final List<File> allFiles = new ArrayList<>();
    private final List<File> files = new ArrayList<>();
    private final Set<String> includedNames = new HashSet<>();
    private String currentName = "";
    private LibraryGroup group = LibraryGroup.ALL;
    private boolean dayNightEnabled;
    private boolean wallpaperActive;

    SlideshowGridAdapter(Context context, PreviewCache previews, boolean engineActive,
                         Listener listener) {
        this.context = context;
        this.previews = previews;
        this.listener = listener;
        dayNight = new DayNightSettings(context);
        refresh(engineActive);
    }

    /** Reloads downloaded files, slideshow membership, and current selection from storage. */
    void refresh(boolean engineActive) {
        wallpaperActive = engineActive;
        allFiles.clear();
        allFiles.addAll(WallpaperStore.listDownloaded(context));
        dayNightEnabled = dayNight.isEnabled();
        List<File> included = WallpaperStore.list(context);
        includedNames.clear();
        for (File file : included) includedNames.add(file.getName());
        File current = WallpaperStore.current(context,
                dayNightEnabled ? included : allFiles);
        currentName = current == null ? "" : current.getName();
        applyGroup();
    }

    /** Shows all downloads or the wallpapers eligible for one scheduled period. */
    void setGroup(LibraryGroup value) {
        group = !dayNightEnabled || value == null ? LibraryGroup.ALL : value;
        applyGroup();
    }

    LibraryGroup group() {
        return group;
    }

    int downloadedCount() {
        return allFiles.size();
    }

    int groupCount(LibraryGroup value) {
        int count = 0;
        for (File file : allFiles) {
            if ((value == null ? LibraryGroup.ALL : value).includes(
                    dayNight.effectiveRole(file))) {
                count++;
            }
        }
        return count;
    }

    private void applyGroup() {
        files.clear();
        for (File file : allFiles) {
            if (!dayNightEnabled || group.includes(dayNight.effectiveRole(file))) files.add(file);
        }
        notifyDataSetChanged();
    }

    int includedCount() {
        return dayNightEnabled ? includedNames.size() : allFiles.size();
    }

    String currentDisplayName() {
        for (File file : allFiles) {
            if (file.getName().equals(currentName)) return WallpaperStore.displayName(file);
        }
        return "Current wallpaper";
    }

    @Override
    public int getCount() {
        return files.size();
    }

    @Override
    public File getItem(int position) {
        return files.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getName().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        File file = getItem(position);
        boolean included = !dayNightEnabled || includedNames.contains(file.getName());
        boolean selected = file.getName().equals(currentName);
        boolean active = selected && wallpaperActive;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 6), Ui.dp(context, 6),
                Ui.dp(context, 6), Ui.dp(context, 6));
        int cardColor = active ? Ui.CYAN_DARK : selected ? Ui.GREEN_DARK : Ui.SURFACE;
        int borderColor = active ? Ui.CYAN : selected ? Ui.GREEN : Ui.DIVIDER;
        card.setBackground(Ui.rounded(cardColor, Ui.dp(context, 14), borderColor,
                Ui.dp(context, active ? 3 : selected ? 2 : 1)));
        card.setContentDescription(WallpaperStore.displayName(file)
                + (active ? ", now showing" : selected ? ", selected" : included
                ? ", included in slideshow" : ", downloaded, not in slideshow"));

        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(context, 10)));
        imageFrame.setClipToOutline(true);
        imageFrame.setClickable(true);
        imageFrame.setFocusable(true);
        imageFrame.setContentDescription("Preview and options for "
                + WallpaperStore.displayName(file));
        imageFrame.setOnClickListener(view -> listener.onOptions(file));
        card.addView(imageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 122)));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageFrame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView loading = Ui.title(context, "LOADING", 11);
        loading.setTextColor(Ui.MUTED);
        loading.setGravity(Gravity.CENTER);
        imageFrame.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String previewKey = file.getAbsolutePath() + ':' + file.lastModified();
        image.setTag(previewKey);
        previews.requestLocal(file, (bitmap, error) -> {
            if (!previewKey.equals(image.getTag())) return;
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                loading.setVisibility(View.GONE);
            } else {
                loading.setText(R.string.no_preview);
            }
        });

        if (selected || !included) {
            String badgeLabel = active ? "NOW SHOWING" : selected ? "SELECTED" : "ON DEVICE";
            int badgeColor = active ? Ui.CYAN : selected ? Ui.GREEN : Ui.SURFACE_HIGH;
            TextView badge = Ui.title(context, badgeLabel, 9);
            badge.setTextColor(selected ? Ui.NAV : Ui.MUTED);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
            badge.setBackground(Ui.rounded(badgeColor, Ui.dp(context, 7),
                    active ? Ui.CYAN : selected ? Ui.GREEN : Ui.DIVIDER, Ui.dp(context, 1)));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 25),
                    Gravity.TOP | Gravity.START);
            badgeParams.setMargins(Ui.dp(context, 7), Ui.dp(context, 7), 0, 0);
            imageFrame.addView(badge, badgeParams);
        }

        if (dayNightEnabled) {
            DayNightRole role = dayNight.effectiveRole(file);
            boolean automatic = dayNight.assignmentMode() == DayNightAssignmentMode.AUTO;
            WallpaperCardControls.addRoleBadge(context, imageFrame,
                    WallpaperStore.displayName(file), role, automatic,
                    view -> listener.onCycleRole(file, role));
        }

        TextView title = Ui.title(context, WallpaperStore.displayName(file), 12);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        title.setShadowLayer(3f, 0f, Ui.dp(context, 1), Color.BLACK);
        title.setBackgroundColor(Color.argb(148, 7, 17, 27));
        imageFrame.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 28), Gravity.BOTTOM));

        WallpaperCardControls.Action set = active
                ? WallpaperCardControls.Action.selected("Showing",
                WallpaperStore.displayName(file) + " is currently showing")
                : selected
                ? WallpaperCardControls.Action.selected("Selected",
                WallpaperStore.displayName(file) + " is selected; activate Deckscape to show it")
                : WallpaperCardControls.Action.standard("Set",
                "Set " + WallpaperStore.displayName(file), true,
                view -> listener.onSet(file));
        WallpaperCardControls.addActionRow(context, card, set,
                WallpaperCardControls.Action.destructive(
                        "Delete " + WallpaperStore.displayName(file) + " from this device",
                        view -> listener.onDelete(file)),
                WallpaperCardControls.Action.standard("Options",
                        "Options for " + WallpaperStore.displayName(file), true,
                        view -> listener.onOptions(file)));

        card.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 180)));
        return card;
    }
}
