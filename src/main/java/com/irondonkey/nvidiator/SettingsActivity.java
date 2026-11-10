// Nvidiator Android: NVDA Settings, with NVDA's own panel names and labels
// (gui/settingsDialogs.py). Only settings that work on Android are shown. Short notes under
// some settings explain the Android side; those notes are our addition.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private TextToSpeech probe;

    private String t(String k) { return UiText.get(this, k); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        // Make sure NVDA's wording and language list are available.
        new Thread(() -> {
            NvdaPy.ensure(this);
            runOnUiThread(this::build);
        }).start();
        Ui.page(this, t("settingsTitle"));
    }

    private void changed() {
        NvidiatorService s = NvidiatorService.get();
        if (s != null) s.reloadSettings();
    }

    private void build() {
        // v0.2: NVDA's settings categories. The list of categories comes first; choosing one shows
        // its panel, and Back returns to the list.
        root = Ui.page(this, t("settingsTitle"));
        cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.VERTICAL);
        root.addView(cats);
        sections.clear();
        LinearLayout col;

        // General
        col = section(t("panelGeneral"));
        List<String> codes = new ArrayList<>(), names = new ArrayList<>();
        codes.add("");
        names.add(getString(R.string.user_default_language));
        try {
            JSONArray langs = new JSONArray(NvdaPy.languages());
            for (int i = 0; i < langs.length(); i++) {
                String code = langs.getJSONArray(i).getString(0);
                codes.add(code);
                Locale l = Locale.forLanguageTag(code.replace('_', '-'));
                String dn = l.getDisplayName(l);
                names.add((dn.isEmpty() ? code : Character.toUpperCase(dn.charAt(0)) + dn.substring(1)) + ", " + code);
            }
        } catch (Exception ignored) {}
        spinner(col, t("language"), names, codes.indexOf(prefs.getString(Prefs.LANGUAGE, "")), i -> {
            String code = codes.get(i);
            if (code.equals(prefs.getString(Prefs.LANGUAGE, ""))) return;
            prefs.edit().putString(Prefs.LANGUAGE, code).apply();
            NvidiatorService s = NvidiatorService.get();
            if (s != null) s.setLanguage(code);
            else new Thread(() -> NvdaPy.setLanguage(this, code)).start();
            UiText.invalidate();
        });
        Ui.text(this, col, getString(R.string.note_language));
        check(col, t("playStartExitSounds"), Prefs.SOUNDS, true);

        // Speech
        col = section(t("panelSpeech"));
        buildSynthChooser(col);
        slider(col, t("rate"), Prefs.RATE, 50);
        slider(col, t("pitch"), Prefs.PITCH, 50);
        slider(col, t("volume"), Prefs.VOLUME, 100);
        List<String> levelNames = new ArrayList<>();
        final int[] levels = {0, 100, 200, 300};
        int cur = 1;
        for (int i = 0; i < levels.length; i++) {
            levelNames.add(t("symbolLevel" + levels[i]));
            if (levels[i] == Prefs.symbolLevel(this)) cur = i;
        }
        spinner(col, t("symbolLevel"), levelNames, cur, i -> { prefs.edit().putInt(Prefs.SYMBOL_LEVEL, levels[i]).apply(); changed(); });
        final String[] modes = {"talk", "beeps", "off"};
        List<String> modeNames = new ArrayList<>();
        int curMode = 0;
        for (int i = 0; i < modes.length; i++) {
            modeNames.add(t("mode" + Character.toUpperCase(modes[i].charAt(0)) + modes[i].substring(1)));
            if (modes[i].equals(Prefs.speechMode(this))) curMode = i;
        }
        spinner(col, t("speechMode"), modeNames, curMode, i -> prefs.edit().putString(Prefs.SPEECH_MODE, modes[i]).apply());

        // Keyboard
        col = section(t("panelKeyboard"));
        Ui.text(this, col, t("selectModifiers"));
        check(col, "Insert", Prefs.KEY_INSERT, true);
        check(col, "Caps Lock", Prefs.KEY_CAPSLOCK, true);
        Ui.text(this, col, getString(R.string.note_keyboard));
        List<String> echo = new ArrayList<>();
        echo.add(t("echoOff"));
        echo.add(t("echoAlways"));
        spinner(col, t("typedChars"), echo, Prefs.typedChars(this) ? 1 : 0, i -> { prefs.edit().putBoolean(Prefs.TYPED_CHARS, i == 1).apply(); changed(); });
        spinner(col, t("typedWords"), echo, Prefs.typedWords(this) ? 1 : 0, i -> { prefs.edit().putBoolean(Prefs.TYPED_WORDS, i == 1).apply(); changed(); });

        // Object Presentation
        col = section(t("panelObjectPresentation"));
        check(col, t("reportNotifications"), Prefs.NOTIFICATIONS, false);
        check(col, t("reportPosition"), Prefs.REPORT_POSITION, true);
        check(col, t("reportDescriptions"), Prefs.REPORT_DESCRIPTIONS, true);
        List<String> progress = new ArrayList<>();
        progress.add(t("progressOff"));
        progress.add(t("progressBeep"));
        spinner(col, t("progressOutput"), progress, Prefs.progressBeeps(this) ? 1 : 0, i -> prefs.edit().putBoolean(Prefs.PROGRESS_BEEPS, i == 1).apply());
        check(col, t("reportDynamic"), Prefs.REPORT_DYNAMIC, true);
        check(col, getString(R.string.report_containers), Prefs.REPORT_CONTAINERS, true);

        // Browse Mode
        col = section(t("panelBrowseMode"));
        check(col, t("modeAudio"), Prefs.MODE_SOUNDS, true);
        check(col, getString(R.string.single_letter_nav), Prefs.QUICK_NAV, true);
        Ui.text(this, col, getString(R.string.note_quick_nav));

        // Touch Interaction
        col = section(t("panelTouch"));
        check(col, t("touchTyping"), Prefs.LIFT_TO_TYPE, true);
        Ui.text(this, col, getString(R.string.note_touch_typing));

        // TalkBack (our addition)
        col = section(getString(R.string.talkback_heading));
        Ui.text(this, col, getString(NvidiatorService.hasSecureSettings(this) ? R.string.talkback_switch_ready : R.string.talkback_switch_setup));

        Ui.button(this, root, t("close"), v -> finish());
    }

    private LinearLayout root, cats, openSection;
    private final List<LinearLayout> sections = new ArrayList<>();

    private LinearLayout section(String title) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setVisibility(View.GONE);
        Ui.heading(this, sec, title, 20);
        root.addView(sec);
        sections.add(sec);
        Ui.button(this, cats, title, v -> showSection(sec));
        return sec;
    }

    private void showSection(LinearLayout sec) {
        cats.setVisibility(View.GONE);
        for (LinearLayout x : sections) x.setVisibility(x == sec ? View.VISIBLE : View.GONE);
        openSection = sec;
        View h = sec.getChildAt(0);
        h.post(() -> h.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED));
        h.post(() -> h.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
    }

    @Override
    public void onBackPressed() {
        if (openSection != null) {
            openSection.setVisibility(View.GONE);
            cats.setVisibility(View.VISIBLE);
            int i = sections.indexOf(openSection);
            openSection = null;
            View c = i >= 0 && i < cats.getChildCount() ? cats.getChildAt(i) : null;
            if (c != null) c.post(() -> c.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
            return;
        }
        super.onBackPressed();
    }

    private void buildSynthChooser(LinearLayout col) {
        final TextView label = Ui.text(this, col, t("synthesizer"));
        final Spinner sp = new Spinner(this);
        sp.setContentDescription(t("synthesizer"));
        col.addView(sp);
        probe = new TextToSpeech(this, status -> runOnUiThread(() -> {
            List<TextToSpeech.EngineInfo> engines = probe.getEngines();
            final List<String> pkgs = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            pkgs.add("");
            labels.add(getString(R.string.phone_default_engine));
            for (TextToSpeech.EngineInfo e : engines) {
                pkgs.add(e.name);
                labels.add(e.label);
            }
            int cur = Math.max(0, pkgs.indexOf(prefs.getString(Prefs.ENGINE, "")));
            bindSpinner(sp, labels, cur, i -> {
                String pkg = pkgs.get(i);
                if (pkg.equals(prefs.getString(Prefs.ENGINE, ""))) return;
                prefs.edit().putString(Prefs.ENGINE, pkg).apply();
                NvidiatorService s = NvidiatorService.get();
                if (s != null) s.restartSpeech();
            });
            probe.shutdown();
        }));
    }

    interface Pick { void picked(int index); }

    private void spinner(LinearLayout col, String label, List<String> items, int selected, Pick pick) {
        TextView l = Ui.text(this, col, label);
        l.setTextColor(Ui.FG);
        l.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Spinner sp = new Spinner(this);
        sp.setContentDescription(label.replaceAll(":\\s*$", ""));
        col.addView(sp);
        bindSpinner(sp, items, Math.max(0, selected), pick);
    }

    private void bindSpinner(Spinner sp, List<String> items, int selected, Pick pick) {
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(Ui.FG);
                    ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                }
                return v;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(selected, false);
        sp.setMinimumHeight(Ui.dp(this, 48));
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { pick.picked(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void check(LinearLayout col, String label, String key, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Ui.FG);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        cb.setMinHeight(Ui.dp(this, 48));
        cb.setChecked(prefs.getBoolean(key, def));
        cb.setOnCheckedChangeListener((v, on) -> { prefs.edit().putBoolean(key, on).apply(); changed(); });
        col.addView(cb);
    }

    private void slider(LinearLayout col, String label, String key, int def) {
        final TextView l = Ui.text(this, col, label + ": " + prefs.getInt(key, def));
        l.setTextColor(Ui.FG);
        l.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(prefs.getInt(key, def));
        sb.setContentDescription(label);
        sb.setKeyProgressIncrement(5);
        sb.setMinimumHeight(Ui.dp(this, 48));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                l.setText(label + ": " + v);
                prefs.edit().putInt(key, v).apply();
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
    }

    @Override
    protected void onDestroy() {
        try { if (probe != null) probe.shutdown(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
