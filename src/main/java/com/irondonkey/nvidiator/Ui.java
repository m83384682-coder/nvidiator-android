// Nvidiator Android: small helpers for building accessible screens in code.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(0x12, 0x12, 0x16);
    static final int FG = Color.rgb(0xF2, 0xF2, 0xF2);
    static final int DIM = Color.rgb(0xB8, 0xB8, 0xC2);
    static final int ACCENT = Color.rgb(0x6C, 0x2D, 0xC7);

    private Ui() {}

    static int dp(Activity a, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics());
    }

    /** A scrolling page with a title heading. Returns the column to add views to. */
    static LinearLayout page(Activity a, String title) {
        a.setTitle(title);
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        col.setPadding(p, p, p, p);
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        a.setContentView(sv);
        heading(a, col, title, 24);
        return col;
    }

    static TextView heading(Activity a, LinearLayout col, String text, int sp) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextColor(FG);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(a, 14), 0, dp(a, 6));
        if (Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        col.addView(t);
        return t;
    }

    static TextView text(Activity a, LinearLayout col, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setPadding(0, dp(a, 4), 0, dp(a, 4));
        t.setTextIsSelectable(false);
        col.addView(t);
        return t;
    }

    static Button button(Activity a, LinearLayout col, String label, View.OnClickListener l) {
        Button b = new Button(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(a, 6), 0, dp(a, 6));
        b.setMinHeight(dp(a, 56));
        b.setOnClickListener(l);
        col.addView(b, lp);
        return b;
    }

    /** A menu item: the button, then a short hint about its gesture or key (our addition to NVDA's menu). */
    static Button item(Activity a, LinearLayout col, String label, String hint, View.OnClickListener l) {
        Button b = button(a, col, label, l);
        if (hint != null && !hint.isEmpty()) {
            b.setContentDescription(label);
            if (Build.VERSION.SDK_INT >= 26) b.setTooltipText(hint);
            TextView t = text(a, col, hint);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        return b;
    }

    static void speak(String text) {
        NvidiatorService s = NvidiatorService.get();
        if (s != null) s.speak(text, true);
    }
}
