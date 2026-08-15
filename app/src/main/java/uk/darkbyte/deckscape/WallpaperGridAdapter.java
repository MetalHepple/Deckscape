package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders category and wallpaper cards, including preview, slideshow, and current states. */
final class WallpaperGridAdapter extends BaseAdapter {
    /** Receives navigation, preview, or wallpaper-selection actions from a card. */
    interface Listener {
        void onAction(CatalogItem item);

        void onPreview(CatalogItem item);

        void onOptions(File file);

        void onDelete(File file);

        void onCycleRole(File file, DayNightRole currentRole);
    }

    private final Context context;
    private final PreviewCache previews;
    private final Listener listener;
    private final List<CatalogItem> allItems = new ArrayList<>();
    private final List<CatalogItem> visibleItems = new ArrayList<>();
    private final Map<String, Integer> downloadProgress = new HashMap<>();
    private final Map<String, File> installedFiles = new HashMap<>();
    private final DayNightSettings dayNight;
    private RepositorySource source;
    private String filter = "";
    private String selectedFileName = "";
    private boolean wallpaperActive;
    private boolean dayNightEnabled;

    WallpaperGridAdapter(Context context, PreviewCache previews, Listener listener) {
        this.context = context;
        this.previews = previews;
        this.listener = listener;
        dayNight = new DayNightSettings(context);
    }

    void setData(RepositorySource repositorySource, List<CatalogItem> items) {
        source = repositorySource;
        allItems.clear();
        allItems.addAll(items);
        applyFilter();
    }

    void filter(String value) {
        filter = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    void refreshLibraryState(boolean engineActive) {
        wallpaperActive = engineActive;
        dayNightEnabled = dayNight.isEnabled();
        installedFiles.clear();
        List<File> downloaded = WallpaperStore.listDownloaded(context);
        for (File file : downloaded) {
            installedFiles.put(file.getName(), file);
        }
        List<File> included = WallpaperStore.list(context);
        File selected = WallpaperStore.current(context,
                dayNightEnabled ? included : downloaded);
        selectedFileName = selected == null ? "" : selected.getName();
        notifyDataSetChanged();
    }

    void setDownloadProgress(RepositorySource itemSource, CatalogItem item, int percent) {
        int value = percent < 0 ? -1 : Math.min(100, percent);
        downloadProgress.put(item.stableKey(itemSource), value);
        notifyDataSetChanged();
    }

    void clearDownloadProgress(RepositorySource itemSource, CatalogItem item) {
        downloadProgress.remove(item.stableKey(itemSource));
        notifyDataSetChanged();
    }

    private void applyFilter() {
        visibleItems.clear();
        for (CatalogItem item : allItems) {
            if (filter.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(filter)
                    || item.path.toLowerCase(Locale.ROOT).contains(filter)) {
                visibleItems.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return visibleItems.size();
    }

    @Override
    public CatalogItem getItem(int position) {
        return visibleItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).path.hashCode();
    }

    /** Returns a stable snapshot so a preview dialog follows the current search results. */
    List<CatalogItem> visibleItemsSnapshot() {
        return new ArrayList<>(visibleItems);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        CatalogItem item = getItem(position);
        return item.isDirectory() ? buildDirectoryCard(item) : buildWallpaperCard(item);
    }

    private View buildDirectoryCard(CatalogItem item) {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(context, 14),
                Ui.DIVIDER, Ui.dp(context, 1)));
        card.setClipToOutline(true);
        card.setOnClickListener(view -> listener.onAction(item));
        card.setContentDescription("Open category " + item.name);

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView placeholder = Ui.title(context, "CATEGORY", 11);
        placeholder.setTextColor(Ui.MUTED);
        placeholder.setGravity(Gravity.CENTER);
        card.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (item.preview != null) {
            String key = item.preview.stableKey(source);
            image.setTag(key);
            previews.request(source, item.preview, (bitmap, error) -> {
                if (!key.equals(image.getTag())) return;
                if (bitmap != null) {
                    image.setImageBitmap(bitmap);
                    placeholder.setVisibility(View.GONE);
                }
            });
        } else {
            ImageView folder = new ImageView(context);
            folder.setImageDrawable(new FolderDrawable(Ui.CYAN));
            FrameLayout.LayoutParams folderParams = new FrameLayout.LayoutParams(
                    Ui.dp(context, 48), Ui.dp(context, 48), Gravity.CENTER);
            folderParams.bottomMargin = Ui.dp(context, 34);
            card.addView(folder, folderParams);
        }

        TextView title = Ui.title(context, item.name, 17);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setPadding(Ui.dp(context, 14), Ui.dp(context, 8),
                Ui.dp(context, 14), Ui.dp(context, 8));
        title.setShadowLayer(5f, 0f, Ui.dp(context, 1), Color.BLACK);
        title.setBackground(Ui.rounded(Color.argb(220, 7, 17, 27), Ui.dp(context, 10),
                Color.argb(180, 66, 217, 232), Ui.dp(context, 1)));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        titleParams.leftMargin = Ui.dp(context, 14);
        titleParams.rightMargin = Ui.dp(context, 14);
        card.addView(title, titleParams);

        TextView categoryBadge = stateBadge("CATEGORY", Ui.CYAN, Ui.NAV);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 25),
                Gravity.TOP | Gravity.START);
        badgeParams.setMargins(Ui.dp(context, 9), Ui.dp(context, 9), 0, 0);
        card.addView(categoryBadge, badgeParams);

        card.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 174)));
        return card;
    }

    private View buildWallpaperCard(CatalogItem item) {
        String key = item.stableKey(source);
        Integer percent = downloadProgress.get(key);
        String destinationName = WallpaperStore.destinationName(source, item);
        File installedFile = installedFiles.get(destinationName);
        boolean installed = installedFile != null;
        boolean selected = destinationName.equals(selectedFileName);
        boolean active = selected && wallpaperActive;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 6), Ui.dp(context, 6),
                Ui.dp(context, 6), Ui.dp(context, 6));
        int cardColor = active ? Ui.CYAN_DARK : installed ? Ui.GREEN_DARK : Ui.SURFACE;
        int borderColor = active ? Ui.CYAN : installed ? Ui.GREEN : Ui.DIVIDER;
        int borderWidth = active ? 3 : installed ? 2 : 1;
        card.setBackground(Ui.rounded(cardColor, Ui.dp(context, 14),
                borderColor, Ui.dp(context, borderWidth)));
        String state = active ? "now showing" : selected ? "ready"
                : installed ? "downloaded on this device" : "not downloaded";
        card.setContentDescription(WallpaperStore.displayName(item.name) + ", " + state);

        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(context, 10)));
        imageFrame.setClipToOutline(true);
        imageFrame.setClickable(true);
        imageFrame.setFocusable(true);
        imageFrame.setContentDescription("Preview " + WallpaperStore.displayName(item.name));
        imageFrame.setOnClickListener(view -> listener.onPreview(item));
        card.addView(imageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 122)));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageFrame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView loading = Ui.text(context, "PREVIEW", 11, Ui.MUTED);
        loading.setGravity(Gravity.CENTER);
        imageFrame.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image.setTag(key);
        previews.request(source, item, (bitmap, error) -> {
            if (!key.equals(image.getTag())) return;
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                loading.setVisibility(View.GONE);
            } else {
                loading.setText(R.string.no_preview);
            }
        });

        if (percent != null) {
            addImageBadge(imageFrame, "DOWNLOADING", Ui.CYAN, Ui.NAV, Gravity.TOP | Gravity.START);
        } else if (active) {
            addImageBadge(imageFrame, "NOW SHOWING", Ui.CYAN, Ui.NAV, Gravity.TOP | Gravity.START);
        } else if (selected) {
            addImageBadge(imageFrame, "SELECTED", Ui.GREEN, Ui.NAV, Gravity.TOP | Gravity.START);
        } else if (installed) {
            addImageBadge(imageFrame, "ON DEVICE", Ui.GREEN, Ui.NAV,
                    Gravity.TOP | Gravity.START);
        }

        if (installed && dayNightEnabled) {
            DayNightRole role = dayNight.effectiveRole(installedFile);
            boolean automatic = dayNight.assignmentMode() == DayNightAssignmentMode.AUTO;
            WallpaperCardControls.addRoleBadge(context, imageFrame,
                    WallpaperStore.displayName(item.name), role, automatic,
                    view -> listener.onCycleRole(installedFile, role));
        } else if (item.isGif()) {
            addImageBadge(imageFrame, "GIF", Ui.CYAN, Ui.NAV, Gravity.TOP | Gravity.END);
        }

        TextView title = Ui.title(context, WallpaperStore.displayName(item.name), 13);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        title.setShadowLayer(3f, 0f, Ui.dp(context, 1), Color.BLACK);
        title.setBackgroundColor(Color.argb(148, 7, 17, 27));
        imageFrame.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 29), Gravity.BOTTOM));

        String displayName = WallpaperStore.displayName(item.name);
        if (percent != null) {
            WallpaperCardControls.addActionRow(context, card,
                    WallpaperCardControls.Action.progress(
                            percent < 0 ? "Starting" : percent + "%",
                            "Downloading " + displayName, percent));
        } else if (!WallpaperRules.canInstall(item)) {
            WallpaperCardControls.addActionRow(context, card,
                    WallpaperCardControls.Action.disabled(
                            context.getString(R.string.too_large),
                            displayName + " exceeds the download size limit"));
        } else if (!installed) {
            WallpaperCardControls.addActionRow(context, card,
                    WallpaperCardControls.Action.primary("Get", "Download " + displayName,
                            view -> listener.onAction(item)));
        } else {
            WallpaperCardControls.Action set = active
                    ? WallpaperCardControls.Action.selected("Showing",
                    displayName + " is currently showing")
                    : selected
                    ? WallpaperCardControls.Action.selected("Selected",
                    displayName + " is selected; activate Deckscape to show it")
                    : WallpaperCardControls.Action.standard("Set", "Set " + displayName,
                    true, view -> listener.onAction(item));
            WallpaperCardControls.addActionRow(context, card, set,
                    WallpaperCardControls.Action.destructive(
                            "Delete " + displayName + " from this device",
                            view -> listener.onDelete(installedFile)),
                    WallpaperCardControls.Action.standard("Options",
                            "Options for " + displayName, true,
                            view -> listener.onOptions(installedFile)));
        }
        card.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 180)));
        return card;
    }

    private void addImageBadge(FrameLayout frame, String label, int fill, int textColor,
                               int gravity) {
        TextView badge = stateBadge(label, fill, textColor);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 25), gravity);
        params.setMargins(Ui.dp(context, 7), Ui.dp(context, 7),
                Ui.dp(context, 7), Ui.dp(context, 7));
        frame.addView(badge, params);
    }

    private TextView stateBadge(String label, int fill, int textColor) {
        TextView badge = Ui.title(context, label, 10);
        badge.setTextColor(textColor);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        badge.setBackground(Ui.rounded(fill, Ui.dp(context, 7)));
        return badge;
    }
}
