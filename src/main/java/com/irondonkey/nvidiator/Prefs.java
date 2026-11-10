// Nvidiator Android: an unofficial, experimental Android port of NVDA's sound and feel.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings, with NVDA's defaults where NVDA has one. */
public final class Prefs {
    public static final String FILE = "nvidiator";

    public static final String ENGINE = "engine";            // TTS engine package, "" = phone default
    public static final String LANGUAGE = "language";        // NVDA locale, "" = phone language
    public static final String RATE = "rate";                // 0-100, NVDA default 50
    public static final String PITCH = "pitch";              // 0-100, NVDA default 50
    public static final String VOLUME = "volume";            // 0-100, NVDA default 100
    public static final String SPEECH_MODE = "speechMode";   // talk, beeps, off
    public static final String SYMBOL_LEVEL = "symbolLevel"; // 0, 100, 200, 300; NVDA default 100 (some)
    public static final String SOUNDS = "sounds";
    public static final String MODE_SOUNDS = "modeSounds";
    public static final String TYPED_CHARS = "typedChars";
    public static final String TYPED_WORDS = "typedWords";
    public static final String REPORT_CONTAINERS = "reportContainers";
    public static final String REPORT_POSITION = "reportPosition";
    public static final String REPORT_DESCRIPTIONS = "reportDescriptions";
    public static final String PROGRESS_BEEPS = "progressBeeps";
    public static final String NOTIFICATIONS = "notifications";
    public static final String QUICK_NAV = "quickNav";
    public static final String KEY_INSERT = "keyInsert";
    public static final String KEY_CAPSLOCK = "keyCapsLock";
    public static final String LIFT_TO_TYPE = "liftToType";
    public static final String REPORT_DYNAMIC = "reportDynamic";

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int i(Context c, String key, int def) { return get(c).getInt(key, def); }
    public static boolean b(Context c, String key, boolean def) { return get(c).getBoolean(key, def); }
    public static String s(Context c, String key, String def) { return get(c).getString(key, def); }

    public static int rate(Context c) { return i(c, RATE, 50); }
    public static int pitch(Context c) { return i(c, PITCH, 50); }
    public static int volume(Context c) { return i(c, VOLUME, 100); }
    public static String speechMode(Context c) { return s(c, SPEECH_MODE, "talk"); }
    public static int symbolLevel(Context c) { return i(c, SYMBOL_LEVEL, 100); }
    public static boolean sounds(Context c) { return b(c, SOUNDS, true); }
    public static boolean modeSounds(Context c) { return b(c, MODE_SOUNDS, true); }
    public static boolean typedChars(Context c) { return b(c, TYPED_CHARS, true); }
    public static boolean typedWords(Context c) { return b(c, TYPED_WORDS, false); }
    public static boolean reportContainers(Context c) { return b(c, REPORT_CONTAINERS, true); }
    public static boolean reportPosition(Context c) { return b(c, REPORT_POSITION, true); }
    public static boolean reportDescriptions(Context c) { return b(c, REPORT_DESCRIPTIONS, true); }
    public static boolean progressBeeps(Context c) { return b(c, PROGRESS_BEEPS, true); }
    public static boolean notifications(Context c) { return b(c, NOTIFICATIONS, false); }
    public static boolean quickNav(Context c) { return b(c, QUICK_NAV, true); }
    public static boolean keyInsert(Context c) { return b(c, KEY_INSERT, true); }
    public static boolean keyCapsLock(Context c) { return b(c, KEY_CAPSLOCK, true); }
    public static boolean liftToType(Context c) { return b(c, LIFT_TO_TYPE, true); }
}
