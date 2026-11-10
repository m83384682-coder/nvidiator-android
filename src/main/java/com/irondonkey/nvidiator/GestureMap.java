// Nvidiator Android: touch gesture bindings, with the user's own changes from Input gestures.
// Plain Java (no Android classes) so it can be tested on a computer.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GestureMap {
    private GestureMap() {}

    /** The command id for "do nothing"; a gesture bound to it is left to Android. */
    public static final String NONE = "none";

    /** Every gesture Nvidiator can bind, in the order Input gestures lists them, with its spoken name. */
    public static final String[][] GESTURES = {
            {"swipeRight", "swipe right"},
            {"swipeLeft", "swipe left"},
            {"swipeUp", "swipe up"},
            {"swipeDown", "swipe down"},
            {"swipeLeftRight", "swipe left then right"},
            {"swipeRightLeft", "swipe right then left"},
            {"swipeUpDown", "swipe up then down"},
            {"swipeDownUp", "swipe down then up"},
            {"swipeDownRight", "swipe down then right"},
            {"swipeDownLeft", "swipe down then left"},
            {"swipeUpLeft", "swipe up then left"},
            {"swipeUpRight", "swipe up then right"},
            {"swipeLeftUp", "swipe left then up"},
            {"swipeLeftDown", "swipe left then down"},
            {"swipeRightUp", "swipe right then up"},
            {"swipeRightDown", "swipe right then down"},
            {"2fingerTap", "2 finger tap"},
            {"2fingerDoubleTap", "2 finger double tap"},
            {"2fingerTripleTap", "2 finger triple tap"},
            {"2fingerDoubleTapHold", "2 finger double tap and hold"},
            {"3fingerTap", "3 finger tap"},
            {"3fingerDoubleTap", "3 finger double tap"},
            {"3fingerTripleTap", "3 finger triple tap"},
            {"3fingerDoubleTapHold", "3 finger double tap and hold"},
            {"3fingerSwipeUp", "3 finger swipe up"},
            {"3fingerSwipeDown", "3 finger swipe down"},
            {"3fingerSwipeLeft", "3 finger swipe left"},
            {"3fingerSwipeRight", "3 finger swipe right"},
            {"4fingerTap", "4 finger tap"},
            {"4fingerDoubleTap", "4 finger double tap"},
            {"4fingerTripleTap", "4 finger triple tap"},
            {"4fingerSwipeUp", "4 finger swipe up"},
            {"4fingerSwipeDown", "4 finger swipe down"},
            {"4fingerSwipeLeft", "4 finger swipe left"},
            {"4fingerSwipeRight", "4 finger swipe right"},
    };

    /** Every command a gesture can run, in the order Input gestures offers them. */
    public static final String[] COMMANDS = {
            "next", "previous", "up", "down", "activate", "current", "focus", "title", "readWindow",
            "sayAll", "stop", "nextWord", "previousWord", "spell", "clipboard", "sleepMode", "touchMode", "ringNext", "ringPrevious",
            "ringUp", "ringDown", "clearEdit", "paste", "copy", "cut", "selectAll", "scrollForward",
            "scrollBackward", "autoScroll", "tapInterrupt", "menu", "settings", "inputGestures", "addonStore",
            "console", "speechViewer", "speechMode", "symbolLevel", "typedChars", "typedWords", "sayCap",
            "time", "battery", "inputHelp", "back", "home", "recents", "notifications", "quickSettings",
            "quit", NONE,
    };

    /** Nvidiator's own bindings, before the user changes anything. */
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("swipeRight", "next");
        DEFAULTS.put("swipeLeft", "previous");
        DEFAULTS.put("swipeUp", "up");
        DEFAULTS.put("swipeDown", "down");
        DEFAULTS.put("swipeLeftRight", "clearEdit");   // v0.2: clears the edit box; elsewhere reports the current item
        DEFAULTS.put("swipeRightLeft", "paste");       // v0.2: pastes; elsewhere moves to the focus
        DEFAULTS.put("swipeUpDown", "touchMode");
        DEFAULTS.put("swipeDownUp", "sayAll");
        DEFAULTS.put("swipeDownRight", "menu");        // TalkBack's menu gesture opens the NVDA menu
        DEFAULTS.put("swipeDownLeft", "back");
        DEFAULTS.put("swipeUpLeft", "home");
        DEFAULTS.put("swipeUpRight", "speechViewer");
        DEFAULTS.put("swipeLeftUp", "recents");
        DEFAULTS.put("swipeLeftDown", "title");
        DEFAULTS.put("swipeRightUp", "time");
        DEFAULTS.put("swipeRightDown", "notifications");
        DEFAULTS.put("2fingerTap", "stop");
        DEFAULTS.put("2fingerDoubleTap", "menu");     // NVDA's ts:2finger_double_tap
        DEFAULTS.put("2fingerTripleTap", "symbolLevel");
        DEFAULTS.put("3fingerTap", "menu");           // TalkBack's menu gesture
        DEFAULTS.put("3fingerDoubleTap", "touchMode"); // NVDA's ts:3finger_tap
        DEFAULTS.put("3fingerTripleTap", "speechMode");
        DEFAULTS.put("3fingerSwipeDown", "sayAll");    // NVDA's ts:3finger_flickDown
        DEFAULTS.put("3fingerSwipeUp", "focus");
        DEFAULTS.put("3fingerSwipeRight", "nextWord");
        DEFAULTS.put("3fingerSwipeLeft", "previousWord");
        DEFAULTS.put("4fingerTap", "inputHelp");
        DEFAULTS.put("4fingerDoubleTap", "console");
        DEFAULTS.put("4fingerSwipeUp", "focus");
        DEFAULTS.put("4fingerSwipeDown", "current");
    }

    public static boolean isGesture(String key) {
        for (String[] g : GESTURES) if (g[0].equals(key)) return true;
        return false;
    }

    public static boolean isCommand(String id) {
        for (String c : COMMANDS) if (c.equals(id)) return true;
        return false;
    }

    public static String gestureName(String key) {
        for (String[] g : GESTURES) if (g[0].equals(key)) return g[1];
        return key;
    }

    /** Reads the saved changes: one "gesture=command" per line. Unknown names are skipped. */
    public static Map<String, String> parse(String saved) {
        Map<String, String> m = new LinkedHashMap<>();
        if (saved == null) return m;
        for (String line : saved.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String g = line.substring(0, eq).trim(), c = line.substring(eq + 1).trim();
            if (isGesture(g) && isCommand(c)) m.put(g, c);
        }
        return m;
    }

    public static String serialize(Map<String, String> overrides) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    /** The command a gesture runs: the user's change if there is one, else the default; null when none. */
    public static String resolve(String gesture, Map<String, String> overrides) {
        String c = overrides != null ? overrides.get(gesture) : null;
        if (c == null) c = DEFAULTS.get(gesture);
        if (c == null || NONE.equals(c)) return null;
        return c;
    }

    /** Sets a gesture's command, dropping the entry when it matches the default. */
    public static void assign(Map<String, String> overrides, String gesture, String command) {
        String def = DEFAULTS.get(gesture);
        if (def == null) def = NONE;
        if (command.equals(def)) overrides.remove(gesture);
        else overrides.put(gesture, command);
    }

    /** Gestures that currently run a command, for the Input gestures list. */
    public static List<String> gesturesFor(String command, Map<String, String> overrides) {
        List<String> out = new ArrayList<>();
        for (String[] g : GESTURES) {
            String c = resolve(g[0], overrides);
            if (command.equals(c)) out.add(g[0]);
        }
        return out;
    }

    /**
     * Whether a binding would leave no gesture for the NVDA menu. Input gestures refuses such a change,
     * so there is always a way to reach Exit.
     */
    public static boolean leavesNoMenu(Map<String, String> overrides) {
        return gesturesFor("menu", overrides).isEmpty();
    }
}
