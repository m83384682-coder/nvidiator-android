// Nvidiator Android: NVDA's speech viewer (speechViewer.py), showing everything spoken.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SpeechViewerActivity extends Activity implements SpeechHistory.Listener {
    private TextView log;
    private ScrollView scroller;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String title = UiText.get(this, "speechViewerTitle");
        setTitle(title);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 16);
        root.setPadding(p, p, p, p);
        Ui.heading(this, root, title, 24);
        Ui.text(this, root, getString(R.string.speech_viewer_note));
        scroller = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(Ui.FG);
        log.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        log.setTextIsSelectable(true);
        // Read with swipes, but do not announce each new line: the speech itself is the announcement.
        log.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        scroller.addView(log);
        root.addView(scroller, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        root.addView(row);
        Ui.button(this, row, getString(R.string.clear), v -> { SpeechHistory.clear(); log.setText(""); });
        Ui.button(this, row, UiText.get(this, "close"), v -> finish());
        setContentView(root);
        StringBuilder sb = new StringBuilder();
        for (String s : SpeechHistory.snapshot()) sb.append(s).append('\n');
        log.setText(sb);
        scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onStart() {
        super.onStart();
        SpeechHistory.addListener(this);
    }

    @Override
    protected void onStop() {
        SpeechHistory.removeListener(this);
        super.onStop();
    }

    @Override
    public void onSpoken(String text) {
        main.post(() -> {
            log.append(text + "\n");
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        });
    }
}
