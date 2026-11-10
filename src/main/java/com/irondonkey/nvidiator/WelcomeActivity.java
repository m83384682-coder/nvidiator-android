// Nvidiator Android: NVDA's welcome dialog (gui/startupDialogs.py, WelcomeDialog), shown on first run.
// The title, text and options use NVDA's own wording and translations; the touch hint under it is ours.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.CheckBox;
import android.widget.LinearLayout;

public class WelcomeActivity extends Activity {
    private String t(String k) { return UiText.get(this, k); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, t("welcomeTitle"));
        Ui.heading(this, col, t("welcomeHeader"), 20);
        Ui.text(this, col, t("welcomeDetail"));
        Ui.text(this, col, getString(R.string.welcome_touch_hint));
        Ui.heading(this, col, t("options"), 18);
        CheckBox caps = box(col, t("capsAsNvda"), Prefs.keyCapsLock(this));
        caps.setOnCheckedChangeListener((v, on) -> Prefs.put(this, Prefs.KEY_CAPSLOCK, on));
        CheckBox show = box(col, t("showWelcome"), Prefs.showWelcome(this));
        show.setOnCheckedChangeListener((v, on) -> Prefs.put(this, Prefs.SHOW_WELCOME, on));
        Ui.button(this, col, t("ok"), v -> finish());
        Prefs.put(this, Prefs.WELCOME_DONE, true);
    }

    private CheckBox box(LinearLayout col, String label, boolean on) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Ui.FG);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        cb.setMinHeight(Ui.dp(this, 48));
        cb.setChecked(on);
        col.addView(cb);
        return cb;
    }

    @Override
    protected void onDestroy() {
        NvidiatorService s = NvidiatorService.get();
        if (s != null) s.reloadSettings();
        super.onDestroy();
    }
}
