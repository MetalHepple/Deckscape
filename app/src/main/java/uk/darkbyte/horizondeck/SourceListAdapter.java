package uk.darkbyte.horizondeck;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class SourceListAdapter extends BaseAdapter {
    private final Context context;
    private final List<RepositorySource> sources = new ArrayList<>();
    private String selectedId = "";

    SourceListAdapter(Context context) {
        this.context = context;
    }

    void setSources(List<RepositorySource> values) {
        sources.clear();
        sources.addAll(values);
        notifyDataSetChanged();
    }

    void setSelected(RepositorySource source) {
        selectedId = source == null ? "" : source.id();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return sources.size();
    }

    @Override
    public RepositorySource getItem(int position) {
        return sources.get(position);
    }

    @Override
    public long getItemId(int position) {
        return sources.get(position).id().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RepositorySource source = getItem(position);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 10),
                Ui.dp(context, 12), Ui.dp(context, 10));
        boolean selected = source.id().equals(selectedId);
        row.setBackground(Ui.rounded(selected ? Ui.CYAN_DARK : Ui.SURFACE,
                Ui.dp(context, 12), selected ? Ui.CYAN : Ui.SURFACE, Ui.dp(context, 1)));

        TextView title = Ui.title(context, source.displayName, 17);
        title.setSingleLine(true);
        row.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = Ui.text(context, source.owner + "/" + source.repository
                + (source.builtIn ? "  •  curated" : "  •  custom"), 12, Ui.MUTED);
        subtitle.setSingleLine(true);
        subtitle.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        row.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0, 0, 0, Ui.dp(context, 8));
        row.setLayoutParams(outer);
        return row;
    }
}
