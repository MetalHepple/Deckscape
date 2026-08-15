package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Shared action row and schedule badge used by Browse and Library wallpaper cards. */
final class WallpaperCardControls {
    static final int ACTION_ROW_HEIGHT_DP = 46;

    /** Describes one equally sized card action. */
    static final class Action {
        final String label;
        final String description;
        final boolean accent;
        final boolean enabled;
        final boolean selected;
        final boolean destructive;
        final Integer progress;
        final View.OnClickListener listener;

        private Action(String label, String description, boolean accent, boolean enabled,
                       boolean selected, boolean destructive, Integer progress,
                       View.OnClickListener listener) {
            this.label = label;
            this.description = description;
            this.accent = accent;
            this.enabled = enabled;
            this.selected = selected;
            this.destructive = destructive;
            this.progress = progress;
            this.listener = listener;
        }

        static Action primary(String label, String description, View.OnClickListener listener) {
            return new Action(label, description, true, true, false, false, null, listener);
        }

        static Action standard(String label, String description, boolean enabled,
                               View.OnClickListener listener) {
            return new Action(label, description, false, enabled, false, false, null, listener);
        }

        static Action selected(String label, String description) {
            return new Action(label, description, false, false, true, false, null, null);
        }

        static Action destructive(String description, View.OnClickListener listener) {
            return new Action("Delete", description, false, true, false, true, null, listener);
        }

        static Action disabled(String label, String description) {
            return new Action(label, description, false, false, false, false, null, null);
        }

        static Action progress(String label, String description, int progress) {
            return new Action(label, description, true, false, false, false,
                    Math.max(-1, Math.min(100, progress)), null);
        }
    }

    private WallpaperCardControls() {}

    static void addActionRow(Context context, LinearLayout card, Action... actions) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(context, 4), 0, 0);
        for (int index = 0; index < actions.length; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (index > 0) params.leftMargin = Ui.dp(context, 4);
            row.addView(actionView(context, actions[index]), params);
        }
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, ACTION_ROW_HEIGHT_DP)));
    }

    static void addRoleBadge(Context context, FrameLayout imageFrame, String displayName,
                             DayNightRole role, boolean automatic,
                             View.OnClickListener listener) {
        TextView badge = Ui.title(context,
                automatic ? "AUTO " + role.name()
                        : (role == DayNightRole.BOTH ? "BOTH" : role.name()) + "  ↻", 9);
        badge.setTextColor(Ui.NAV);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        badge.setBackground(Ui.rounded(role == DayNightRole.NIGHT
                ? Ui.CORAL : Ui.CYAN, Ui.dp(context, 7)));

        FrameLayout target = new FrameLayout(context);
        target.setClickable(!automatic);
        target.setFocusable(!automatic);
        target.setContentDescription(automatic
                ? displayName + " is sorted automatically as " + role.label
                : "Change " + displayName + " from " + role.label
                + " to " + role.next().label);
        if (!automatic) target.setOnClickListener(listener);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 25),
                Gravity.TOP | Gravity.END);
        badgeParams.topMargin = Ui.dp(context, 7);
        target.addView(badge, badgeParams);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Ui.dp(context, automatic ? 116 : 96), Ui.dp(context, 48),
                Gravity.TOP | Gravity.END);
        params.setMargins(0, 0, Ui.dp(context, 7), 0);
        imageFrame.addView(target, params);
    }

    private static FrameLayout actionView(Context context, Action action) {
        FrameLayout box = new FrameLayout(context);
        Button button = Ui.actionButton(context, action.label, action.accent);
        button.setSingleLine(true);
        button.setTextSize(10);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0);
        button.setEnabled(action.enabled);
        if (action.description != null) button.setContentDescription(action.description);
        if (action.listener != null) button.setOnClickListener(action.listener);
        if (action.selected) {
            button.setTextColor(Ui.CYAN);
            button.setBackground(Ui.rounded(Ui.CYAN_DARK, Ui.dp(context, 10),
                    Ui.CYAN, Ui.dp(context, 2)));
        } else if (action.destructive) {
            button.setTextColor(Ui.CORAL);
            button.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(context, 10),
                    Ui.CORAL, Ui.dp(context, 1)));
        }
        box.addView(button, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (action.progress != null) {
            ProgressBar bar = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setIndeterminate(action.progress < 0);
            if (action.progress >= 0) bar.setProgress(action.progress);
            bar.setProgressTintList(ColorStateList.valueOf(Ui.CYAN));
            bar.setIndeterminateTintList(ColorStateList.valueOf(Ui.CYAN));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.CYAN_DARK));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 4), Gravity.BOTTOM);
            params.setMargins(Ui.dp(context, 9), 0, Ui.dp(context, 9), Ui.dp(context, 5));
            box.addView(bar, params);
        }
        return box;
    }
}
