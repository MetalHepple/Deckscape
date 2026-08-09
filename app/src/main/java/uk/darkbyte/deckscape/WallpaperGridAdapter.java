package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Renders category and wallpaper cards, including preview, slideshow, and current states. */
final class WallpaperGridAdapter extends BaseAdapter {
    /** Receives navigation, preview, or wallpaper-selection actions from a card. */
    interface Listener {
        void onAction(CatalogItem item);

        void onPreview(CatalogItem item);
    }

    private final Context context;
    private final PreviewCache previews;
    private final Listener listener;
    private final List<CatalogItem> allItems = new ArrayList<>();
    private final List<CatalogItem> visibleItems = new ArrayList<>();
    private final Map<String, Integer> downloadProgress = new HashMap<>();
    private final Set<String> installedNames = new HashSet<>();
    private RepositorySource source;
    private String filter = "";
    private String selectedFileName = "";
    private boolean wallpaperActive;

    WallpaperGridAdapter(Context context, PreviewCache previews, Listener listener) {
        this.context = context;
        this.previews = previews;
        this.listener = listener;
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
        installedNames.clear();
        List<File> files = WallpaperStore.list(context);
        for (File file : files) installedNames.add(file.getName());
        File selected = WallpaperStore.current(context, files);
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
        boolean installed = installedNames.contains(destinationName);
        boolean selected = destinationName.equals(selectedFileName);
        boolean active = selected && wallpaperActive;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 6), Ui.dp(context, 6),
                Ui.dp(context, 6), Ui.dp(context, 6));
        int cardColor = active ? Ui.CYAN_DARK : selected ? Ui.GREEN_DARK : Ui.SURFACE;
        int borderColor = active ? Ui.CYAN : selected || installed ? Ui.GREEN : Ui.DIVIDER;
        int borderWidth = active || selected ? 3 : 1;
        card.setBackground(Ui.rounded(cardColor, Ui.dp(context, 14),
                borderColor, Ui.dp(context, borderWidth)));
        String state = active ? "now showing" : selected ? "ready"
                : installed ? "included in slideshow" : "not downloaded";
        card.setContentDescription(item.name + ", " + state);

        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(context, 10)));
        imageFrame.setClipToOutline(true);
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
            addImageBadge(imageFrame, "READY", Ui.GREEN, Ui.NAV, Gravity.TOP | Gravity.START);
        } else if (installed) {
            addImageBadge(imageFrame, "IN SLIDESHOW", Ui.GREEN, Ui.NAV, Gravity.TOP | Gravity.START);
        }

        if (item.isGif()) {
            addImageBadge(imageFrame, "GIF", Ui.CYAN, Ui.NAV, Gravity.TOP | Gravity.END);
        }

        TextView title = Ui.title(context, item.name, 16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0);
        title.setShadowLayer(4f, 0f, Ui.dp(context, 1), Color.BLACK);
        title.setBackgroundColor(Color.argb(224, 7, 17, 27));
        imageFrame.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 38), Gravity.BOTTOM));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, Ui.dp(context, 4), 0, 0);

        Button preview = Ui.actionButton(context, "Preview", false);
        preview.setSingleLine(true);
        preview.setTextSize(12);
        preview.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        preview.setContentDescription("Preview " + item.name);
        preview.setOnClickListener(view -> listener.onPreview(item));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        actions.addView(preview, previewParams);

        FrameLayout actionBox = buildAction(item, percent, installed, selected, active);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        actionParams.leftMargin = Ui.dp(context, 6);
        actions.addView(actionBox, actionParams);
        card.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 46)));
        return card;
    }

    private FrameLayout buildAction(CatalogItem item, Integer percent, boolean installed,
                                    boolean selected, boolean active) {
        FrameLayout box = new FrameLayout(context);
        String label = !WallpaperRules.canInstall(item) ? context.getString(R.string.too_large)
                : percent != null ? (percent < 0 ? "Starting" : percent + "%")
                : active ? "Showing"
                : selected ? "Ready"
                : installed ? "Show now" : "Download";
        boolean enabled = WallpaperRules.canInstall(item)
                && percent == null && !active && !selected;
        Button action = Ui.actionButton(context, label, !installed || selected);
        action.setSingleLine(true);
        action.setTextSize(12);
        action.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        action.setEnabled(enabled);
        if (!enabled && percent == null) action.setTextColor(active ? Ui.CYAN : Ui.MUTED);
        if (active) {
            action.setBackground(Ui.rounded(Ui.CYAN_DARK, Ui.dp(context, 10),
                    Ui.CYAN, Ui.dp(context, 2)));
        } else if (installed) {
            action.setTextColor(Ui.GREEN);
            action.setBackground(Ui.rounded(Ui.GREEN_DARK, Ui.dp(context, 10),
                    Ui.GREEN, Ui.dp(context, 1)));
        }
        action.setOnClickListener(view -> listener.onAction(item));
        box.addView(action, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (percent != null) {
            ProgressBar bar = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setIndeterminate(percent < 0);
            if (percent >= 0) bar.setProgress(percent);
            bar.setProgressTintList(ColorStateList.valueOf(Ui.CYAN));
            bar.setIndeterminateTintList(ColorStateList.valueOf(Ui.CYAN));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.CYAN_DARK));
            FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 4), Gravity.BOTTOM);
            barParams.setMargins(Ui.dp(context, 9), 0, Ui.dp(context, 9), Ui.dp(context, 5));
            box.addView(bar, barParams);
        }
        return box;
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
