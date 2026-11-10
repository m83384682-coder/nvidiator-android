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
    // v0.2
    public static final String AUTO_SCROLL = "autoScroll";              // scroll lists at the end, Android only
    public static final String TAP_INTERRUPT = "tapInterrupt";          // stop speech on double tap / double tap and hold
    public static final String SAY_CAP = "sayCap";                      // NVDA sayCapForCapitals, default off
    public static final String BEEP_CAPS = "beepCaps";                  // NVDA beepForCapitals, default off
    public static final String CAP_PITCH = "capPitch";                  // NVDA capPitchChange, default 30
    public static final String INTERRUPT_TYPED = "interruptTyped";      // NVDA speechInterruptForCharacters, default on
    public static final String DELAYED_DESCRIPTIONS = "delayedDescriptions"; // NVDA delayedCharacterDescriptions, default off
    public static final String SUGGESTION_SOUNDS = "suggestionSounds";  // NVDA reportAutoSuggestionsWithSound, default on
    public static final String SOUND_VOLUME_FOLLOWS = "soundVolumeFollows"; // NVDA soundVolumeFollowsVoice, default off
    public static final String SOUND_VOLUME = "soundVolume";            // NVDA soundVolume, default 100
    public static final String CLIPBOARD_SOUNDS = "clipboardSounds";    // NVDA Remote clipboard sounds for copy and paste
    public static final String CONNECTION_SOUNDS = "connectionSounds";  // NVDA Remote connected/disconnected sounds for internet
    public static final String SPEAK_COMMAND_KEYS = "speakCommandKeys"; // NVDA speakCommandKeys, default off
    public static final String LOCK_ANNOUNCE = "lockAnnounce";          // announce screen locked / unlocked
    public static final String SHOW_WELCOME = "showWelcomeDialogAtStartup"; // NVDA's welcome dialog checkbox
    public static final String WELCOME_DONE = "welcomeShown";
    public static final String RATE_BOOST = "rateBoost";                // NVDA rate boost, default off
    public static final String REPORT_TOOLTIPS = "reportTooltips";      // NVDA reportTooltips, default off
    public static final String GESTURE_MAP = "gestureMap";              // JSON of user gesture overrides
    public static final String ADDON_WARNING = "addonStoreWarning";     // NVDA showWarning for add-on store

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
    public static boolean reportDynamic(Context c) { return b(c, REPORT_DYNAMIC, true); }
    public static boolean autoScroll(Context c) { return b(c, AUTO_SCROLL, true); }
    public static boolean tapInterrupt(Context c) { return b(c, TAP_INTERRUPT, true); }
    public static boolean sayCap(Context c) { return b(c, SAY_CAP, false); }
    public static boolean beepCaps(Context c) { return b(c, BEEP_CAPS, false); }
    public static int capPitch(Context c) { return i(c, CAP_PITCH, 30); }
    public static boolean interruptTyped(Context c) { return b(c, INTERRUPT_TYPED, true); }
    public static boolean delayedDescriptions(Context c) { return b(c, DELAYED_DESCRIPTIONS, false); }
    public static boolean suggestionSounds(Context c) { return b(c, SUGGESTION_SOUNDS, true); }
    public static boolean soundVolumeFollows(Context c) { return b(c, SOUND_VOLUME_FOLLOWS, false); }
    public static int soundVolume(Context c) { return i(c, SOUND_VOLUME, 100); }
    public static boolean clipboardSounds(Context c) { return b(c, CLIPBOARD_SOUNDS, true); }
    public static boolean connectionSounds(Context c) { return b(c, CONNECTION_SOUNDS, false); }
    public static boolean speakCommandKeys(Context c) { return b(c, SPEAK_COMMAND_KEYS, false); }
    public static boolean lockAnnounce(Context c) { return b(c, LOCK_ANNOUNCE, true); }
    public static boolean showWelcome(Context c) { return b(c, SHOW_WELCOME, false); } // v0.2: first run only, unless ticked
    public static boolean rateBoost(Context c) { return b(c, RATE_BOOST, false); }
    public static boolean reportTooltips(Context c) { return b(c, REPORT_TOOLTIPS, false); }
    public static void put(Context c, String key, boolean v) { get(c).edit().putBoolean(key, v).apply(); }
    public static void put(Context c, String key, int v) { get(c).edit().putInt(key, v).apply(); }
    public static void put(Context c, String key, String v) { get(c).edit().putString(key, v).apply(); }
}
