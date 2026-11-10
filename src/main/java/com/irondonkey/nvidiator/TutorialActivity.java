// Nvidiator Android: a short tutorial and NVDA's Commands Quick Reference for Android gestures
// and keys. This screen is our addition; NVDA's command names are used where NVDA has them.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

public class TutorialActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean quickRef = getIntent().getBooleanExtra("quickRef", false);
        LinearLayout col = Ui.page(this, quickRef ? UiText.get(this, "menuQuickRef") : getString(R.string.tutorial_title));
        if (!quickRef) {
            Ui.text(this, col, getString(R.string.tutorial_intro));
            String[] steps = getResources().getStringArray(R.array.tutorial_steps);
            for (int i = 0; i < steps.length; i++) {
                Ui.heading(this, col, getString(R.string.tutorial_step, i + 1), 18);
                Ui.text(this, col, steps[i]);
            }
            Ui.heading(this, col, getString(R.string.practice_heading), 20);
            Ui.text(this, col, getString(R.string.practice_text));
            Ui.button(this, col, getString(R.string.practice_start), v -> {
                NvidiatorService s = NvidiatorService.get();
                if (s != null) s.runCommand("inputHelp");
                else Ui.speak(getString(R.string.not_running));
            });
        }
        Ui.heading(this, col, getString(R.string.ref_touch_heading), 20);
        for (String line : getResources().getStringArray(R.array.ref_touch)) Ui.text(this, col, line);
        Ui.heading(this, col, getString(R.string.ref_keys_heading), 20);
        for (String line : getResources().getStringArray(R.array.ref_keys)) Ui.text(this, col, line);
        Ui.heading(this, col, getString(R.string.ref_quicknav_heading), 20);
        for (String line : getResources().getStringArray(R.array.ref_quicknav)) Ui.text(this, col, line);
        Ui.button(this, col, UiText.get(this, "close"), v -> finish());
    }
}
