package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Color;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Renders every downloaded wallpaper with explicit rotation and deletion controls. */
final class SlideshowGridAdapter extends BaseAdapter {
    /** Receives wallpaper-library actions selected from a card. */
    interface Listener {
        void onShowNow(File file);

        void onSetIncluded(File file, boolean included);

        void onOptions(File file);
    }

    private final Context context;
    private final PreviewCache previews;
    private final Listener listener;
    private final WallpaperProfileStore profiles;
    private final List<File> files = new ArrayList<>();
    private final Set<String> includedNames = new HashSet<>();
    private String currentName = "";

    SlideshowGridAdapter(Context context, PreviewCache previews, Listener listener) {
        this.context = context;
        this.previews = previews;
        this.listener = listener;
        profiles = new WallpaperProfileStore(context);
        refresh();
    }

    /** Reloads downloaded files, slideshow membership, and current selection from storage. */
    void refresh() {
        files.clear();
        files.addAll(WallpaperStore.listDownloaded(context));
        List<File> included = WallpaperStore.list(context);
        includedNames.clear();
        for (File file : included) includedNames.add(file.getName());
        File current = WallpaperStore.current(context, included);
        currentName = current == null ? "" : current.getName();
        notifyDataSetChanged();
    }

    int includedCount() {
        return includedNames.size();
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
        boolean included = includedNames.contains(file.getName());
        boolean current = file.getName().equals(currentName);
        WallpaperProfile profile = profiles.get(file);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 6), Ui.dp(context, 6),
                Ui.dp(context, 6), Ui.dp(context, 6));
        int cardColor = current ? Ui.CYAN_DARK : included ? Ui.GREEN_DARK : Ui.SURFACE;
        int borderColor = current ? Ui.CYAN : included ? Ui.GREEN : Ui.DIVIDER;
        card.setBackground(Ui.rounded(cardColor, Ui.dp(context, 14), borderColor,
                Ui.dp(context, current ? 3 : 1)));
        card.setContentDescription(WallpaperStore.displayName(file)
                + (current ? ", now showing" : included
                ? ", included in slideshow" : ", downloaded, not in slideshow"));

        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(context, 10)));
        imageFrame.setClipToOutline(true);
        card.addView(imageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 110)));

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

        String badgeLabel = current ? "NOW SHOWING" : included ? "IN SLIDESHOW" : "ON DEVICE";
        int badgeColor = current ? Ui.CYAN : included ? Ui.GREEN : Ui.SURFACE_HIGH;
        TextView badge = Ui.title(context, badgeLabel, 9);
        badge.setTextColor(current || included ? Ui.NAV : Ui.MUTED);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        badge.setBackground(Ui.rounded(badgeColor, Ui.dp(context, 7),
                current ? Ui.CYAN : included ? Ui.GREEN : Ui.DIVIDER, Ui.dp(context, 1)));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 25),
                Gravity.TOP | Gravity.START);
        badgeParams.setMargins(Ui.dp(context, 7), Ui.dp(context, 7), 0, 0);
        imageFrame.addView(badge, badgeParams);

        TextView roleBadge = Ui.title(context,
                profile.role == DayNightRole.BOTH ? "BOTH" : profile.role.name(), 9);
        roleBadge.setTextColor(Ui.NAV);
        roleBadge.setGravity(Gravity.CENTER);
        roleBadge.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        roleBadge.setBackground(Ui.rounded(profile.role == DayNightRole.NIGHT
                        ? Ui.CORAL : Ui.CYAN, Ui.dp(context, 7)));
        FrameLayout.LayoutParams roleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 25),
                Gravity.TOP | Gravity.END);
        roleParams.setMargins(0, Ui.dp(context, 7), Ui.dp(context, 7), 0);
        imageFrame.addView(roleBadge, roleParams);

        TextView title = Ui.title(context, WallpaperStore.displayName(file), 12);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        title.setShadowLayer(3f, 0f, Ui.dp(context, 1), Color.BLACK);
        title.setBackgroundColor(Color.argb(148, 7, 17, 27));
        imageFrame.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 28), Gravity.BOTTOM));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, Ui.dp(context, 4), 0, 0);

        Button show = compactButton(current ? "Showing" : "Show", current);
        show.setSingleLine(true);
        show.setEnabled(included && !current);
        show.setOnClickListener(view -> listener.onShowNow(file));
        actions.addView(show, weightedButtonParams(false));

        Button membership = compactButton(included ? "Remove" : "Add", !included);
        membership.setTextColor(included ? Ui.GREEN : Ui.CYAN);
        membership.setOnClickListener(view -> listener.onSetIncluded(file, !included));
        actions.addView(membership, weightedButtonParams(true));

        Button options = compactButton("Options", false);
        options.setOnClickListener(view -> listener.onOptions(file));
        actions.addView(options, weightedButtonParams(true));

        card.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 46)));

        card.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 170)));
        return card;
    }

    private Button compactButton(String label, boolean accent) {
        Button button = Ui.actionButton(context, label, accent);
        button.setSingleLine(true);
        button.setTextSize(10);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0);
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams(boolean withMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (withMargin) params.leftMargin = Ui.dp(context, 4);
        return params;
    }
}
