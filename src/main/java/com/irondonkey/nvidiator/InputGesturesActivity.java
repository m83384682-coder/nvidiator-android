// Nvidiator Android: NVDA's Input Gestures dialog (gui/inputGestures.py), for touch gestures.
// Titles and buttons use NVDA's own wording and translations. Each gesture shows the command it runs;
// tapping it lets you choose another command. The NVDA menu always keeps at least one gesture.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InputGesturesActivity extends Activity {
    private Map<String, String> overrides;
    private LinearLayout list;
    private String filter = "";

    private String t(String k) { return UiText.get(this, k); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        overrides = GestureMap.parse(Prefs.s(this, Prefs.GESTURE_MAP, ""));
        LinearLayout col = Ui.page(this, t("inputGesturesTitle"));
        Ui.text(this, col, t("gestFilter"));
        EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setTextColor(Ui.FG);
        f.setContentDescription(t("gestFilter"));
        f.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { filter = s.toString().trim().toLowerCase(Locale.ROOT); fill(); }
        });
        col.addView(f);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        Ui.button(this, col, t("gestReset"), v -> new AlertDialog.Builder(this)
                .setTitle(t("gestResetTitle"))
                .setMessage(t("gestResetQuestion"))
                .setPositiveButton(t("yes"), (d, w) -> { overrides.clear(); save(); fill(); })
                .setNegativeButton(t("no"), null)
                .show());
        Ui.button(this, col, t("close"), v -> finish());
        fill();
    }

    private String commandLabel(String id) {
        if (GestureMap.NONE.equals(id)) return getString(R.string.gesture_unassigned);
        NvidiatorService s = NvidiatorService.get();
        String d = s != null ? s.commandDescription(id) : null;
        return d == null || d.isEmpty() ? id : d;
    }

    private void fill() {
        list.removeAllViews();
        for (String[] g : GestureMap.GESTURES) {
            String cmd = GestureMap.resolve(g[0], overrides);
            String label = g[1] + ": " + commandLabel(cmd == null ? GestureMap.NONE : cmd);
            if (!filter.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(filter)) continue;
            Button btn = Ui.button(this, list, label, v -> pick(g[0], g[1]));
            btn.setContentDescription(label);
        }
        // Gestures that add-ons bind run before Nvidiator's own; they are listed here for reference.
        JSONArray ab = Addons.gestureBindings();
        if (ab.length() > 0) {
            Ui.heading(this, list, getString(R.string.gesture_addon_section), 20);
            Ui.text(this, list, getString(R.string.gesture_restart_hint));
            for (int i = 0; i < ab.length(); i++) {
                JSONObject o = ab.optJSONObject(i);
                if (o == null) continue;
                String line = o.optString("gesture") + ": " + o.optString("description", o.optString("script")) + " (" + o.optString("addon") + ")";
                if (!filter.isEmpty() && !line.toLowerCase(Locale.ROOT).contains(filter)) continue;
                Ui.text(this, list, line);
            }
        }
    }

    private void pick(String gesture, String name) {
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String c : GestureMap.COMMANDS) { ids.add(c); labels.add(commandLabel(c)); }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.gesture_pick_command, name))
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String before = overrides.containsKey(gesture) ? overrides.get(gesture) : null;
                    GestureMap.assign(overrides, gesture, ids.get(which));
                    if (GestureMap.leavesNoMenu(overrides)) {
                        if (before == null) overrides.remove(gesture); else overrides.put(gesture, before);
                        Ui.speak(getString(R.string.gesture_menu_required));
                        new AlertDialog.Builder(this).setMessage(getString(R.string.gesture_menu_required))
                                .setPositiveButton(t("ok"), null).show();
                        return;
                    }
                    save();
                    fill();
                })
                .setNegativeButton(t("cancel"), null)
                .show();
    }

    private void save() {
        Prefs.put(this, Prefs.GESTURE_MAP, GestureMap.serialize(overrides));
        NvidiatorService s = NvidiatorService.get();
        if (s != null) s.reloadGestures();
    }
}
