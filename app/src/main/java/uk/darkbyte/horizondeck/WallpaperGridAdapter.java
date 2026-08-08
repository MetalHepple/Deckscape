package uk.darkbyte.horizondeck;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WallpaperGridAdapter extends BaseAdapter {
    interface Listener {
        void onAction(CatalogItem item);
    }

    private final Context context;
    private final PreviewCache previews;
    private final Listener listener;
    private final List<CatalogItem> allItems = new ArrayList<>();
    private final List<CatalogItem> visibleItems = new ArrayList<>();
    private RepositorySource source;
    private String filter = "";

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
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(context, 15), Ui.dp(context, 12),
                Ui.dp(context, 13), Ui.dp(context, 12));
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(context, 14),
                Ui.DIVIDER, Ui.dp(context, 1)));
        card.setOnClickListener(view -> listener.onAction(item));
        card.setContentDescription("Open category " + item.name);

        ImageView folder = new ImageView(context);
        folder.setImageDrawable(new FolderDrawable(Ui.CYAN));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 34), Ui.dp(context, 34));
        iconParams.rightMargin = Ui.dp(context, 12);
        card.addView(folder, iconParams);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.title(context, item.name, 15);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title);
        TextView meta = Ui.text(context, "Browse folder", 11, Ui.MUTED);
        copy.addView(meta);
        card.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = Ui.title(context, "›", 26);
        arrow.setTextColor(Ui.CYAN);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(
                Ui.dp(context, 28), Ui.dp(context, 42)));
        card.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 76)));
        return card;
    }

    private View buildWallpaperCard(CatalogItem item) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 6), Ui.dp(context, 6),
                Ui.dp(context, 6), Ui.dp(context, 6));
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(context, 14),
                Ui.DIVIDER, Ui.dp(context, 1)));

        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(context, 10)));
        imageFrame.setClipToOutline(true);
        card.addView(imageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 118)));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageFrame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView loading = Ui.text(context, "PREVIEW", 11, Ui.MUTED);
        loading.setGravity(Gravity.CENTER);
        imageFrame.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String key = item.stableKey(source);
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

        if (item.isGif()) {
            TextView badge = Ui.title(context, "GIF", 11);
            badge.setTextColor(Ui.CYAN);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(Ui.dp(context, 8), Ui.dp(context, 3),
                    Ui.dp(context, 8), Ui.dp(context, 3));
            badge.setBackground(Ui.rounded(Ui.NAV, Ui.dp(context, 8),
                    Ui.CYAN, Ui.dp(context, 1)));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            badgeParams.setMargins(0, 0, Ui.dp(context, 7), Ui.dp(context, 7));
            imageFrame.addView(badge, badgeParams);
        }

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.HORIZONTAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        details.setPadding(0, Ui.dp(context, 3), 0, 0);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.title(context, item.name, 14);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 24)));
        TextView meta = Ui.text(context, item.humanSize(), 11, Ui.MUTED);
        copy.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 18)));
        details.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        Button action = Ui.actionButton(context, "Apply", true);
        if (!WallpaperRules.canInstall(item)) {
            action.setText(R.string.too_large);
            action.setEnabled(false);
            action.setTextColor(Ui.MUTED);
        }
        action.setOnClickListener(view -> listener.onAction(item));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 78), Ui.dp(context, 40));
        actionParams.leftMargin = Ui.dp(context, 8);
        details.addView(action, actionParams);
        card.addView(details, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 50)));
        return card;
    }
}
