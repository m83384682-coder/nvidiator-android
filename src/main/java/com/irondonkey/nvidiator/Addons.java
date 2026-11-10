package com.irondonkey.nvidiator;

import android.content.Context;
import android.util.Log;

import com.chaquo.python.PyObject;

import org.json.JSONArray;

import java.io.File;

/**
 * Java side of add-on support. All add-on code runs in Python (addonHandler.py); this class only
 * calls it, and never lets an add-on error reach the screen reader. Calls that run add-on code
 * (start, restart, gestures) must run on the Python thread, not the main thread.
 * Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
 * Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
 */
final class Addons {
    private static final String TAG = "Nvidiator";
    private static volatile boolean started = false;

    private Addons() {}

    static File dir(Context c) { return new File(c.getApplicationContext().getFilesDir(), "addons"); }

    private static PyObject handler() { return NvdaPy.module("addonHandler"); }

    /** Loads the enabled add-ons. Returns the ones that failed and were turned off (summary, reason). */
    static JSONArray start(Context c) {
        if (!NvdaPy.ready()) return new JSONArray();
        try {
            String r = handler().callAttr("initialize", dir(c).getAbsolutePath()).toString();
            started = true;
            refreshWanted();
            return new JSONArray(r);
        } catch (Throwable t) {
            Log.e(TAG, "add-ons failed to start", t);
            return new JSONArray();
        }
    }

    static JSONArray restart() {
        if (!started) return new JSONArray();
        try {
            JSONArray r = new JSONArray(handler().callAttr("restart").toString());
            refreshWanted();
            return r;
        } catch (Throwable t) {
            Log.e(TAG, "add-ons failed to restart", t);
            return new JSONArray();
        }
    }

    static void stop() {
        if (!started) return;
        try { handler().callAttr("terminate"); } catch (Throwable t) { Log.w(TAG, "add-on terminate", t); }
        started = false;
        refreshWanted();
    }

    static boolean started() { return started; }

    /** Result of offering a gesture to the add-ons. */
    static final class GestureResult {
        final boolean handled;
        final String failedAddon, reason;
        GestureResult(boolean handled, String failedAddon, String reason) {
            this.handled = handled; this.failedAddon = failedAddon; this.reason = reason;
        }
    }

    /** Runs the add-on script bound to an NVDA gesture identifier such as "ts:2finger_double_tap". */
    static GestureResult gesture(String identifier, String displayName) {
        if (!started) return new GestureResult(false, null, null);
        try {
            org.json.JSONObject o = new org.json.JSONObject(handler().callAttr("runGesture", identifier, displayName).toString());
            return new GestureResult(o.optBoolean("handled"), o.has("failed") ? o.optString("failed") : null, o.optString("reason", ""));
        } catch (Throwable t) {
            Log.w(TAG, "add-on gesture", t);
            return new GestureResult(false, null, null);
        }
    }

    static String summaryOf(String name) {
        try {
            JSONArray a = list();
            for (int i = 0; i < a.length(); i++) {
                if (name.equals(a.getJSONObject(i).optString("name"))) return a.getJSONObject(i).optString("summary", name);
            }
        } catch (Exception ignored) {}
        return name;
    }

    static JSONArray list() {
        try { return new JSONArray(handler().callAttr("listAddons").toString()); }
        catch (Throwable t) { return new JSONArray(); }
    }

    static JSONArray gestureBindings() {
        if (!started) return new JSONArray();
        try { return new JSONArray(handler().callAttr("gestureBindings").toString()); }
        catch (Throwable t) { return new JSONArray(); }
    }

    /** Reads a package's manifest. Throws with NVDA's reason when it is not a valid add-on. */
    static org.json.JSONObject bundleInfo(String path) throws Exception {
        return new org.json.JSONObject(handler().callAttr("bundleInfo", path).toString());
    }

    static String install(String path) throws Exception {
        return handler().callAttr("installBundle", path).toString();
    }

    static void setEnabled(String name, boolean enabled) {
        try { handler().callAttr("setEnabled", name, enabled); } catch (Throwable t) { Log.w(TAG, "setEnabled", t); }
    }

    static void remove(String name) {
        try { handler().callAttr("remove", name); } catch (Throwable t) { Log.w(TAG, "remove", t); }
    }

    static boolean restartPending() {
        try { return handler().callAttr("isRestartPending").toBoolean(); } catch (Throwable t) { return false; }
    }

    /** NVDA's touch gesture identifier for one of Nvidiator's gestures, e.g. 2fingerDoubleTap -> ts:2finger_double_tap. */
    static String tsIdentifier(String key) {
        String fingers = "";
        String rest = key;
        if (key.length() > 7 && Character.isDigit(key.charAt(0)) && key.startsWith("finger", 1)) {
            fingers = key.charAt(0) + "finger_";
            rest = key.substring(7);
        }
        String r;
        switch (rest) {
            case "Tap": r = "tap"; break;
            case "DoubleTap": r = "double_tap"; break;
            case "TripleTap": r = "triple_tap"; break;
            case "DoubleTapHold": r = "double_tapAndHold"; break;
            default:
                // swipeRight -> flickRight, swipeLeftRight -> flickLeft_flickRight; 3fingerSwipeUp -> 3finger_flickUp
                String s = rest.startsWith("swipe") ? rest.substring(5) : rest.startsWith("Swipe") ? rest.substring(5) : rest;
                StringBuilder b = new StringBuilder();
                int i = 0;
                while (i < s.length()) {
                    int j = i + 1;
                    while (j < s.length() && Character.isLowerCase(s.charAt(j))) j++;
                    if (b.length() > 0) b.append('_');
                    b.append("flick").append(s, i, j);
                    i = j;
                }
                r = b.toString();
        }
        return "ts:" + fingers + r;
    }

    // ---- v0.2: events and speech hooks ----------------------------------------------------------

    private static volatile java.util.Set<String> wantedEvents = java.util.Collections.emptySet();
    private static volatile boolean speechHooks = false;

    /** Reads which events the running add-ons handle. Call on the Python thread after any change. */
    static void refreshWanted() {
        if (!started) { wantedEvents = java.util.Collections.emptySet(); speechHooks = false; return; }
        try {
            org.json.JSONObject o = new org.json.JSONObject(handler().callAttr("wantedInfo").toString());
            java.util.Set<String> w = new java.util.HashSet<>();
            JSONArray a = o.optJSONArray("events");
            if (a != null) for (int i = 0; i < a.length(); i++) w.add(a.optString(i));
            wantedEvents = w;
            speechHooks = o.optBoolean("speech");
        } catch (Throwable t) {
            Log.w(TAG, "add-on wanted events", t);
        }
    }

    static boolean wants(String event) { return started && wantedEvents.contains(event); }

    static boolean speechHooks() { return started && speechHooks; }

    /** Sends an event to the add-ons. Run on the Python thread. Returns add-ons that failed and were turned off. */
    static JSONArray event(String name, String nodeJson, String extraJson) {
        if (!started) return new JSONArray();
        try {
            JSONArray r = new JSONArray(handler().callAttr("dispatchEvent", name, nodeJson, extraJson == null ? "" : extraJson).toString());
            if (r.length() > 0) refreshWanted();
            return r;
        } catch (Throwable t) {
            Log.w(TAG, "add-on event " + name, t);
            return new JSONArray();
        }
    }

    /** Speaks text through the add-ons' speech hooks. Run on the Python thread. */
    static void speakThrough(String text, boolean interrupt) {
        handler().callAttr("speakThrough", text, interrupt);
    }
}
