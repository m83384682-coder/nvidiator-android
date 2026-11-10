// Nvidiator Android: reads the clipboard for NVDA's "report clipboard text" command. Android only
// lets the app in front read the clipboard, so this see-through screen opens, reads it once it has
// focus, hands the text to the screen reader and closes at once.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;

public class ClipboardActivity extends Activity {
    private boolean done = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        CharSequence text = null;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) text = clip.getItemAt(0).coerceToText(this);
        } catch (Throwable ignored) {
        }
        NvidiatorService s = NvidiatorService.get();
        CharSequence ft = text;
        finish();
        overridePendingTransition(0, 0);
        if (s != null) s.main.postDelayed(() -> s.reportClipboard(ft), 250);
    }
}
