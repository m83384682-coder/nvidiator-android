// Nvidiator Android: NVDA's Python console (pythonConsole.py), running real Python through
// Chaquopy. NVDA's PythonConsole class does the work; this activity replaces its wx window.
// Type or paste code, then tap Run. One line runs like NVDA's prompt; several pasted lines run
// as one script. focus, nav, fg and caretObj are live AccessibilityNodeInfo objects.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chaquo.python.PyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PythonConsoleActivity extends Activity {
    private static final ExecutorService py = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView output, prompt;
    private EditText input;
    private ScrollView scroller;
    private PyObject console;
    private static final List<String> history = new ArrayList<>();
    private int historyPos = 0;
    private final StringBuilder pendingSpeech = new StringBuilder();
    private final Runnable flushSpeech = this::speakOutput;

    /** Passed to pythonConsole.initialize(): NVDA's console calls output, setPrompt and close. */
    public class Bridge {
        public void output(String data) {
            main.post(() -> {
                output.append(data);
                scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
                // Like NVDA's console, speak what the code printed (not the echoed input).
                if (!data.startsWith(">>> ") && !data.startsWith("... ")) {
                    pendingSpeech.append(data);
                    main.removeCallbacks(flushSpeech);
                    main.postDelayed(flushSpeech, 60);
                }
            });
        }

        public void setPrompt(String p) { main.post(() -> prompt.setText(p)); }

        public void close() { main.post(PythonConsoleActivity.this::finish); }
    }

    private void rowButton(LinearLayout row, String label, View.OnClickListener l) {
        android.widget.Button btn = Ui.button(this, row, label, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int m = Ui.dp(this, 2);
        lp.setMargins(m, m, m, m);
        btn.setLayoutParams(lp);
        btn.setSingleLine(false);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setMinHeight(Ui.dp(this, 48));
    }

    private void speakOutput() {
        String s = pendingSpeech.toString().trim();
        pendingSpeech.setLength(0);
        if (s.isEmpty()) return;
        NvidiatorService svc = NvidiatorService.get();
        if (svc != null) svc.speak(s, false);
        else output.announceForAccessibility(s);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String title = UiText.get(this, "pythonConsoleTitle");
        setTitle(title);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 12);
        root.setPadding(p, p, p, p);
        Ui.heading(this, root, title, 22);

        scroller = new ScrollView(this);
        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextColor(Ui.FG);
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        output.setTextIsSelectable(true);
        output.setContentDescription(null);
        scroller.addView(output);
        root.addView(scroller, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        // v0.2: the code box is bigger and the buttons sit in two rows under it, so they stay on
        // screen with the keyboard open (the window resizes instead of hiding them).

        prompt = new TextView(this);
        prompt.setText(">>>");
        prompt.setTypeface(Typeface.MONOSPACE);
        prompt.setTextColor(Ui.DIM);
        root.addView(prompt);

        input = new EditText(this);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextColor(Ui.FG);
        input.setHintTextColor(Ui.DIM);
        input.setHint(getString(R.string.console_input_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setMinLines(5);
        input.setMaxLines(14);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setVerticalScrollBarEnabled(true);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setOnKeyListener((v, keyCode, ev) -> onInputKey(keyCode, ev));
        root.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row1 = new LinearLayout(this), row2 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row1);
        root.addView(row2);
        rowButton(row1, getString(R.string.console_run), v -> runInput());
        rowButton(row1, getString(R.string.console_complete), v -> complete());
        rowButton(row1, getString(R.string.console_previous), v -> historyMove(-1));
        rowButton(row2, getString(R.string.console_clear_output), v -> output.setText(""));
        rowButton(row2, UiText.get(this, "close"), v -> finish());
        setContentView(root);

        output.setText(getString(R.string.console_loading) + "\n");
        py.execute(() -> {
            if (!NvdaPy.ensure(this)) {
                main.post(() -> output.append(getString(R.string.console_failed) + " " + NvdaPy.error() + "\n"));
                return;
            }
            try {
                console = NvdaPy.module("pythonConsole");
                String banner = console.callAttr("initialize", new Bridge()).toString();
                String prmpt = console.callAttr("getPrompt").toString();
                main.post(() -> {
                    output.setText(banner);
                    prompt.setText(prmpt);
                    input.requestFocus();
                });
            } catch (Throwable t) {
                main.post(() -> output.append(getString(R.string.console_failed) + " " + t + "\n"));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // NVDA refreshes focus, nav and fg each time the console is opened.
        if (console != null) py.execute(() -> { try { console.callAttr("refreshSnapshot"); } catch (Throwable ignored) {} });
    }

    private boolean onInputKey(int keyCode, KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
        String text = input.getText().toString();
        boolean singleLine = !text.contains("\n");
        if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && !ev.isShiftPressed() && singleLine) {
            runInput();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_TAB) { complete(); return true; }
        if (singleLine && keyCode == KeyEvent.KEYCODE_DPAD_UP) { historyMove(-1); return true; }
        if (singleLine && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { historyMove(1); return true; }
        return false;
    }

    private void runInput() {
        if (console == null) return;
        final String code = input.getText().toString();
        input.setText("");
        if (!code.trim().isEmpty()) {
            history.add(code);
            if (history.size() > 200) history.remove(0);
        }
        historyPos = history.size();
        py.execute(() -> {
            try {
                console.callAttr("pushBlock", code);
            } catch (Throwable t) {
                main.post(() -> output.append(t + "\n"));
            }
        });
    }

    private void complete() {
        if (console == null) return;
        final int cursor = input.getSelectionStart();
        final String before = input.getText().toString().substring(0, Math.max(0, cursor));
        py.execute(() -> {
            try {
                String add = console.callAttr("complete", before).toString();
                String all = add.isEmpty() ? console.callAttr("completions", before).toString() : "";
                main.post(() -> {
                    if (!add.isEmpty()) {
                        input.getText().insert(input.getSelectionStart(), add);
                        Ui.speak(add);
                    } else if (!all.isEmpty()) {
                        output.append(all + "\n");
                        Ui.speak(all.replace("\n", ", "));
                    }
                });
            } catch (Throwable ignored) {}
        });
    }

    private void historyMove(int dir) {
        if (history.isEmpty()) return;
        historyPos = Math.max(0, Math.min(history.size(), historyPos + dir));
        String v = historyPos < history.size() ? history.get(historyPos) : "";
        input.setText(v);
        input.setSelection(v.length());
        Ui.speak(v.isEmpty() ? " " : v);
    }
}
