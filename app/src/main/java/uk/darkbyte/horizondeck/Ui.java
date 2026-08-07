package uk.darkbyte.horizondeck;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = Color.rgb(7, 17, 27);
    static final int NAV = Color.rgb(8, 19, 31);
    static final int SURFACE = Color.rgb(14, 26, 40);
    static final int SURFACE_HIGH = Color.rgb(20, 37, 55);
    static final int CYAN = Color.rgb(66, 217, 232);
    static final int CYAN_DARK = Color.rgb(19, 74, 88);
    static final int CORAL = Color.rgb(255, 122, 89);
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
        button.setTextSize(15);
        button.setTextColor(primary ? Color.rgb(5, 21, 29) : TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        button.setBackground(rounded(primary ? CYAN : SURFACE_HIGH, 14, primary ? CYAN : DIVIDER, 1));
        return button;
    }

    static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }

    static GradientDrawable rounded(int color, float radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(strokeDp, strokeColor);
        return drawable;
    }

    static ViewGroup.LayoutParams size(Context context, int widthDp, int heightDp) {
        return new ViewGroup.LayoutParams(dp(context, widthDp), dp(context, heightDp));
    }
}
