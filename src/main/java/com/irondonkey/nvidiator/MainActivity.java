// Nvidiator Android: the app's opening screen. Explains the safety steps before the screen
// reader is turned on, and opens Android's accessibility settings.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, getString(R.string.app_name));
        Ui.text(this, col, getString(R.string.main_intro));
        status = Ui.text(this, col, "");
        Ui.heading(this, col, getString(R.string.safety_heading), 20);
        Ui.text(this, col, getString(R.string.safety_text));
        Ui.button(this, col, getString(R.string.open_a11y_settings), v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        Ui.button(this, col, getString(R.string.tutorial_title), v -> startActivity(new Intent(this, TutorialActivity.class)));
        Ui.button(this, col, UiText.get(this, "menuSettings"), v -> startActivity(new Intent(this, SettingsActivity.class)));
        Ui.button(this, col, UiText.get(this, "menuPythonConsole"), v -> startActivity(new Intent(this, PythonConsoleActivity.class)));
        Ui.button(this, col, getString(R.string.switch_from_talkback), v -> {
            if (NvidiatorService.hasSecureSettings(this) && TalkBackSwitch.toNvidiator(this)) {
                status.setText(getString(R.string.switching));
            } else {
                status.setText(getString(R.string.talkback_switch_setup));
            }
        });
        Ui.heading(this, col, getString(R.string.talkback_heading), 20);
        Ui.text(this, col, getString(R.string.talkback_switch_setup));
        Ui.button(this, col, UiText.get(this, "menuAbout"), v -> startActivity(new Intent(this, AboutActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = NvidiatorService.get() != null;
        status.setText(getString(on ? R.string.status_on : R.string.status_off));
    }
}
