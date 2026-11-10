// Nvidiator Android: the NVDA menu, with NVDA's own menu wording (gui/__init__.py).
// Each item also says which gesture or key opens it, which NVDA's menu does not.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

public class NvdaMenuActivity extends Activity {
    private String section = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        section = getIntent().getStringExtra("section");
        if (section == null) section = "";
        build();
    }

    private String t(String k) { return UiText.get(this, k); }

    private void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }

    private void sub(String s) {
        Intent i = new Intent(this, NvdaMenuActivity.class);
        i.putExtra("section", s);
        startActivity(i);
    }

    private void build() {
        LinearLayout col;
        switch (section) {
            case "preferences":
                col = Ui.page(this, t("menuPreferences"));
                Ui.item(this, col, t("menuSettings"), getString(R.string.hint_settings), v -> open(SettingsActivity.class));
                Ui.item(this, col, t("menuSaveConfig"), getString(R.string.hint_save_config), v -> {
                    Ui.speak(getString(R.string.config_saved));
                    finish();
                });
                Ui.item(this, col, t("menuResetConfig"), getString(R.string.hint_reset_config), v -> {
                    String engine = Prefs.s(this, Prefs.ENGINE, "");
                    Prefs.get(this).edit().clear().putInt("starts", 3).putString(Prefs.ENGINE, engine).apply();
                    NvidiatorService s = NvidiatorService.get();
                    if (s != null) s.reloadSettings();
                    Ui.speak(getString(R.string.config_reset));
                    finish();
                });
                break;
            case "tools":
                col = Ui.page(this, t("menuTools"));
                Ui.item(this, col, t("menuSpeechViewer"), getString(R.string.hint_speech_viewer), v -> open(SpeechViewerActivity.class));
                Ui.item(this, col, t("menuPythonConsole"), getString(R.string.hint_python_console), v -> open(PythonConsoleActivity.class));
                break;
            case "help":
                col = Ui.page(this, t("menuHelp"));
                Ui.item(this, col, getString(R.string.tutorial_title), getString(R.string.hint_tutorial), v -> open(TutorialActivity.class));
                Ui.item(this, col, t("menuQuickRef"), getString(R.string.hint_quick_ref), v -> {
                    Intent i = new Intent(this, TutorialActivity.class);
                    i.putExtra("quickRef", true);
                    startActivity(i);
                    finish();
                });
                Ui.item(this, col, t("menuLicense"), null, v -> {
                    Intent i = new Intent(this, AboutActivity.class);
                    i.putExtra("license", true);
                    startActivity(i);
                    finish();
                });
                Ui.item(this, col, t("menuAbout"), null, v -> open(AboutActivity.class));
                break;
            case "exit":
                col = Ui.page(this, t("exitTitle"));
                Ui.text(this, col, t("exitQuestion"));
                Ui.item(this, col, t("exitExit"), getString(R.string.hint_exit), v -> {
                    finish();
                    NvidiatorService s = NvidiatorService.get();
                    if (s != null) s.exitScreenReader(false);
                });
                Ui.item(this, col, getString(R.string.exit_to_talkback),
                        getString(NvidiatorService.hasSecureSettings(this) ? R.string.hint_exit_talkback : R.string.hint_exit_talkback_no_perm), v -> {
                    finish();
                    NvidiatorService s = NvidiatorService.get();
                    if (s != null) s.exitScreenReader(true);
                });
                Ui.item(this, col, t("exitRestart"), getString(R.string.hint_restart), v -> {
                    finish();
                    NvidiatorService s = NvidiatorService.get();
                    if (s != null) s.restartSpeech();
                });
                Ui.button(this, col, t("cancel"), v -> finish());
                break;
            default:
                col = Ui.page(this, getString(R.string.menu_title));
                Ui.text(this, col, getString(R.string.menu_intro));
                Ui.item(this, col, t("menuPreferences"), null, v -> sub("preferences"));
                Ui.item(this, col, t("menuTools"), null, v -> sub("tools"));
                Ui.item(this, col, t("menuHelp"), null, v -> sub("help"));
                Ui.item(this, col, t("menuExit"), getString(R.string.hint_exit_menu), v -> sub("exit"));
                break;
        }
    }
}
