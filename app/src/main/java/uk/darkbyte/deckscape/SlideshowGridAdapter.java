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
import java.util.List;

/** Renders the downloaded wallpapers included in Deckscape's slideshow. */
final class SlideshowGridAdapter extends BaseAdapter {
    /** Receives requests to make an included wallpaper the currently displayed item. */
    interface Listener {
        void onShowNow(File file);
    }

    private final Context context;
    private final PreviewCache previews;
    private final Listener listener;
    private final List<File> files = new ArrayList<>();
    private String currentName = "";

    SlideshowGridAdapter(Context context, PreviewCache previews, List<File> included,
                         File current, Listener listener) {
        this.context = context;
        this.previews = previews;
        this.listener = listener;
        files.addAll(included);
        currentName = current == null ? "" : current.getName();
    }

    void setCurrent(File current) {
        currentName = current == null ? "" : current.getName();
        notifyDataSetChanged();
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
        boolean current = file.getName().equals(currentName);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 6), Ui.dp(context, 6),
                Ui.dp(context, 6), Ui.dp(context, 6));
        card.setBackground(Ui.rounded(current ? Ui.CYAN_DARK : Ui.SURFACE,
                Ui.dp(context, 14), current ? Ui.CYAN : Ui.GREEN,
                Ui.dp(context, current ? 3 : 1)));
        card.setContentDescription(WallpaperStore.displayName(file)
                + (current ? ", now showing" : ", included in slideshow"));

        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(context, 10)));
        imageFrame.setClipToOutline(true);
        card.addView(imageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 108)));

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

        TextView badge = Ui.title(context, current ? "NOW SHOWING" : "IN SLIDESHOW", 10);
        badge.setTextColor(current ? Ui.NAV : Color.rgb(5, 29, 21));
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        badge.setBackground(Ui.rounded(current ? Ui.CYAN : Ui.GREEN, Ui.dp(context, 7)));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 25),
                Gravity.TOP | Gravity.START);
        badgeParams.setMargins(Ui.dp(context, 7), Ui.dp(context, 7), 0, 0);
        imageFrame.addView(badge, badgeParams);

        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(Ui.dp(context, 4), Ui.dp(context, 3), 0, 0);
        TextView title = Ui.title(context, WallpaperStore.displayName(file), 13);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footer.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        Button show = Ui.actionButton(context, current ? "Showing" : "Show now", current);
        show.setSingleLine(true);
        show.setTextSize(11);
        show.setPadding(Ui.dp(context, 5), 0, Ui.dp(context, 5), 0);
        show.setEnabled(!current);
        show.setOnClickListener(view -> {
            setCurrent(file);
            listener.onShowNow(file);
        });
        LinearLayout.LayoutParams showParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 78), Ui.dp(context, 40));
        showParams.leftMargin = Ui.dp(context, 6);
        footer.addView(show, showParams);
        card.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 48)));

        card.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 168)));
        return card;
    }
}
