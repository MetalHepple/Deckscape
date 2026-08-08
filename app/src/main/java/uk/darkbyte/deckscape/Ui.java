package uk.darkbyte.deckscape;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** Shared color tokens and small native-view factories for the dependency-free interface. */
final class Ui {
    static final int BACKGROUND = Color.rgb(7, 17, 27);
    static final int NAV = Color.rgb(8, 19, 31);
    static final int SURFACE = Color.rgb(13, 27, 42);
    static final int SURFACE_HIGH = Color.rgb(20, 38, 56);
    static final int SURFACE_SELECTED = Color.rgb(17, 55, 69);
    static final int CYAN = Color.rgb(66, 217, 232);
    static final int CYAN_DARK = Color.rgb(19, 74, 88);
    static final int CORAL = Color.rgb(255, 122, 89);
    static final int GREEN = Color.rgb(98, 224, 164);
    static final int GREEN_DARK = Color.rgb(18, 66, 54);
    static final int TEXT = Color.rgb(244, 248, 251);
    static final int MUTED = Color.rgb(160, 180, 194);
    static final int DIVIDER = Color.rgb(35, 53, 70);

    private Ui() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, String value, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    static TextView title(Context context, String value, float sp) {
        TextView view = text(context, value, sp, TEXT);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return view;
    }

    static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.rgb(5, 21, 29) : TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        button.setBackground(rounded(primary ? CYAN : SURFACE_HIGH, dp(context, 12),
                primary ? CYAN : DIVIDER, dp(context, 1)));
        return button;
    }

    static Button chip(Context context, String label, boolean selected) {
        Button button = button(context, label, false);
        button.setTextSize(13);
        button.setTextColor(selected ? NAV : TEXT);
        button.setTypeface(Typeface.create("sans",
                selected ? Typeface.BOLD : Typeface.NORMAL));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setBackground(rounded(selected ? CYAN : SURFACE_HIGH,
                dp(context, 10), selected ? CYAN : DIVIDER,
                dp(context, selected ? 2 : 1)));
        return button;
    }

    static Button actionButton(Context context, String label, boolean accent) {
        Button button = button(context, label, false);
        button.setTextSize(12);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(accent ? CYAN : TEXT);
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setBackground(rounded(SURFACE_HIGH, dp(context, 10),
                accent ? CYAN : DIVIDER, dp(context, 1)));
        return button;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    static GradientDrawable rounded(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable drawable = rounded(color, radiusPx);
        drawable.setStroke(strokePx, strokeColor);
        return drawable;
    }

    static ViewGroup.LayoutParams size(Context context, int widthDp, int heightDp) {
        return new ViewGroup.LayoutParams(dp(context, widthDp), dp(context, heightDp));
    }
}
