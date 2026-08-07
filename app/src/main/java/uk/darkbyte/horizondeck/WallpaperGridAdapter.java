package uk.darkbyte.horizondeck;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
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
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 8), Ui.dp(context, 8),
                Ui.dp(context, 8), Ui.dp(context, 8));
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(context, 14),
                Ui.DIVIDER, Ui.dp(context, 1)));

        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(context, 10)));
        card.addView(imageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 118)));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true);
        imageFrame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (item.isDirectory()) {
            TextView folder = Ui.title(context, "CATEGORY", 18);
            folder.setTextColor(Ui.CYAN);
            folder.setGravity(Gravity.CENTER);
            imageFrame.addView(folder, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView loading = Ui.text(context, "PREVIEW", 12, Ui.MUTED);
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
        }

        if (item.isGif()) {
            TextView badge = Ui.title(context, "GIF", 11);
            badge.setTextColor(Color.rgb(5, 21, 29));
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(Ui.dp(context, 8), Ui.dp(context, 3),
                    Ui.dp(context, 8), Ui.dp(context, 3));
            badge.setBackground(Ui.rounded(Ui.CORAL, Ui.dp(context, 9)));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            badgeParams.setMargins(0, Ui.dp(context, 7), Ui.dp(context, 7), 0);
            imageFrame.addView(badge, badgeParams);
        }

        TextView title = Ui.title(context, item.name, 15);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 30));
        titleParams.topMargin = Ui.dp(context, 4);
        card.addView(title, titleParams);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView meta = Ui.text(context, item.isDirectory() ? "Browse folder" : item.humanSize(),
                12, Ui.MUTED);
        footer.addView(meta, new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f));

        Button action = Ui.button(context, item.isDirectory() ? "Open" : "Set", false);
        action.setTextSize(13);
        action.setTypeface(Typeface.create("sans", Typeface.BOLD));
        if (!item.isDirectory()) {
            File installed = WallpaperStore.installedFile(context, source, item);
            if (installed != null) action.setText(R.string.use_wallpaper);
            if (!WallpaperRules.canInstall(item)) {
                action.setText(R.string.too_large);
                action.setEnabled(false);
                action.setTextColor(Ui.MUTED);
            }
        }
        action.setOnClickListener(view -> listener.onAction(item));
        footer.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 42)));
        card.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 42)));

        GridViewLayoutParams.setMargins(card, Ui.dp(context, 5));
        return card;
    }

    private static final class GridViewLayoutParams {
        private GridViewLayoutParams() {}

        static void setMargins(View view, int margin) {
            android.widget.AbsListView.LayoutParams params = new android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(params);
            view.setPadding(view.getPaddingLeft() + margin, view.getPaddingTop(),
                    view.getPaddingRight() + margin, view.getPaddingBottom());
        }
    }
}
