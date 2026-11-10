// Nvidiator Android: the screen reader itself.
// An unofficial, experimental port of NVDA's sound and feel to Android, built on NVDA's source
// (https://github.com/nvaccess/nvda). Not affiliated with or endorsed by NV Access.
// Licensed under the GNU GPL version 2.
//
// On Windows NVDA's core watches IAccessible/UIA events. Here an AccessibilityService does that
// job and hands each object to NVDA's Python code (controlTypes, characterProcessing and the
// ported getPropertiesSpeech) to decide what to say. NVDA's sounds and beeps are played as NVDA
// plays them, and NVDA's touch gestures and keyboard commands are mapped onto Android.
package com.irondonkey.nvidiator;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.app.KeyguardManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NvidiatorService extends AccessibilityService {
    private static final String TAG = "Nvidiator";
    private static volatile NvidiatorService instance;

    public static NvidiatorService get() { return instance; }

    final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pyThread = Executors.newSingleThreadExecutor();
    Speech speech;

    // Navigator object (NVDA's review position) and focus.
    private AccessibilityNodeInfo navigator;
    private AccessibilityNodeInfo focus;
    private List<AccessibilityNodeInfo> lastContainers = new ArrayList<>();
    private boolean textMode = false;           // NVDA's touch modes: object or text
    private boolean passThrough = false;        // NVDA's focus mode for quick navigation keys
    private boolean inEditField = false;
    private boolean inputHelp = false;

    // Review cursor for text mode on non-editable objects.
    private String reviewText = "";
    private int reviewPos = 0;

    // Text echo state.
    private long lastTextChange = 0;
    private int lastSelStart = -1, lastSelEnd = -1;
    private AccessibilityNodeInfo lastSelNode;

    // Touch.
    private AccessibilityNodeInfo lastHover;
    private boolean lastHoverIme = false;
    private boolean touchExploring = false;
    // v0.2: speech interrupt on double tap / double tap and hold (the WhatsApp bug)
    private final TapTracker tapTracker = new TapTracker();
    private AccessibilityNodeInfo touchFirstNode;   // the first item explored during this touch
    private AccessibilityNodeInfo holdGuardNode;    // don't re-read this item while the finger is held
    private long holdGuardUntil = 0;
    private final Runnable holdCheck = () -> {
        if (tapTracker.checkHold(SystemClock.uptimeMillis()) == TapTracker.Result.HOLD) interruptForTap();
    };
    // v0.2: customizable gestures
    private Map<String, String> gestureOverrides;
    // v0.2: screen lock announcements and the PIN fix
    private BroadcastReceiver screenReceiver;
    private long lastPasswordEvent = 0;
    private long pinStarSpokenAt = 0;
    private String starText;   // NVDA's word for "*", cached so PIN typing never waits on Python
    private AudioDeviceCallback audioDevices;
    private boolean audioDevicesPrimed = false;
    private boolean gestureInProgress = false;

    // Say all.
    private List<AccessibilityNodeInfo> sayAllList;
    private boolean sayAllActive = false;

    // Misc.
    private int lastProgress = -1;
    private String lastLive = "";
    private long lastLiveTime = 0;
    private String lastStateSpoken = "";
    private long lastQuitPress = 0;
    private long lastTimePress = 0;
    private int settingsRingIndex = 0;
    private boolean nvdaKeyDown = false;
    private boolean nvdaKeyUsed = false;
    private AccessibilityNodeInfo ownFocusTarget;
    // Snapshot for the Python console (NVDA's focus, nav, fg snapshot variables).
    private AccessibilityNodeInfo snapFocus, snapNav, snapFg;

    private final Runnable windowChanged = this::onWindowSettled;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onServiceConnected() {
        instance = this;
        Sounds.init(this);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null && Build.VERSION.SDK_INT >= 30) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
                    | AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH;
            setServiceInfo(info);
        }
        speech = new Speech(this);
        speech.setCallback(new Speech.UtteranceCallback() {
            @Override public void onStart(String id) { onUtteranceStart(id); }
            @Override public void onDone(String id) { onUtteranceDone(id); }
        });
        Sounds.play("start");
        speech.start(this::onSpeechReady);
        registerV02Receivers();
        // NVDA's Python modules start in the background; plain speech works until they are ready.
        pyThread.execute(() -> {
            boolean ok = NvdaPy.ensure(this);
            main.post(() -> {
                if (ok) {
                    try {
                        NvdaPy.module("nvidiator.androidApi").callAttr("setService", this);
                    } catch (Throwable t) { Log.w(TAG, "setService", t); }
                    // v0.2: NVDA's welcome dialog, on first run (and later only if its checkbox is ticked).
                    if (!Prefs.b(this, Prefs.WELCOME_DONE, false) || Prefs.showWelcome(this)) {
                        main.postDelayed(() -> {
                            try { openActivity(WelcomeActivity.class); } catch (Throwable t) { Log.w(TAG, "welcome", t); }
                        }, 1500);
                    }
                    // v0.2: load add-ons. A failing add-on is turned off and reported, never fatal.
                    pyThread.execute(() -> {
                        JSONArray failed = Addons.start(this);
                        main.post(() -> reportAddonFailures(failed));
                    });
                } else {
                    Sounds.play("error");
                }
            });
        });
        // Safety net: if no speech engine ever starts, turn ourselves off so touch works normally again.
        main.postDelayed(() -> {
            if (speech != null && !speech.ready()) {
                Log.e(TAG, "No speech after 15 s, turning off so the phone stays usable");
                Sounds.play("error");
                main.postDelayed(this::disableSelfSafely, 1500);
            }
        }, 15000);
    }

    private void onSpeechReady() {
        if (speech.failed()) {
            if (!Prefs.s(this, Prefs.ENGINE, "").isEmpty()) {
                // The chosen engine failed: fall back to the phone's default engine.
                Prefs.get(this).edit().putString(Prefs.ENGINE, "").apply();
                speech.restart();
                main.postDelayed(() -> { if (speech.ready()) onSpeechReady(); }, 3000);
            }
            return;
        }
        int starts = Prefs.i(this, "starts", 0);
        Prefs.get(this).edit().putInt("starts", starts + 1).apply();
        StringBuilder sb = new StringBuilder();
        if (starts < 3) {
            sb.append(getString(R.string.start_hint)).append(" ");
        }
        if (isTalkBackOn()) sb.append(getString(R.string.talkback_also_on)).append(" ");
        if (sb.length() > 0) speech.speak(sb.toString(), false, null, false);
        main.postDelayed(this::onWindowSettled, 400);
    }

    @Override
    public void onInterrupt() {
        if (speech != null) speech.cancel();
        stopSayAll();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        unregisterV02Receivers();
        main.removeCallbacks(holdCheck);
        try { Addons.stop(); } catch (Throwable ignored) {}
        if (instance == this) {
            instance = null;
            try { NvdaPy.module("nvidiator.androidApi").callAttr("setService", (Object) null); } catch (Throwable ignored) {}
        }
        if (speech != null) {
            speech.shutdown();
            speech = null;
        }
    }

    private void disableSelfSafely() {
        try {
            if (Build.VERSION.SDK_INT >= 24) disableSelf();
        } catch (Throwable t) {
            Log.e(TAG, "disableSelf", t);
        }
    }

    // ------------------------------------------------------------------ speech helpers

    public void speak(String text) { speak(text, true); }

    public void speak(String text, boolean interrupt) {
        if (sleeping()) return;
        // v0.2: when an add-on registered a speech filter or pre-speech handler, speech goes through
        // NVDA's speech extension points in Python. Otherwise nothing changes and nothing waits.
        if (Addons.speechHooks() && text != null && !text.trim().isEmpty()) {
            if (interrupt && speech != null) speech.cancel();
            pyThread.execute(() -> {
                try { Addons.speakThrough(text, interrupt); }
                catch (Throwable t) { Log.w(TAG, "speech hooks", t); main.post(() -> speakDirect(text, interrupt)); }
            });
            return;
        }
        speakDirect(text, interrupt);
    }

    /** Speaks without add-on speech hooks: for speech that already went through them. */
    void speakDirect(String text, boolean interrupt) {
        if (speech != null) speech.speak(text, interrupt);
    }

    // ---- v0.2: events for add-ons (event_gainFocus and friends), sent only when an add-on handles them

    private AccessibilityNodeInfo lastAddonFocus;
    private String lastForegroundPkg = "";

    private void addonEvent(String name, AccessibilityNodeInfo n) {
        if (!Addons.wants(name)) return;
        final String js;
        try { js = n == null ? "" : Nodes.toJson(n).toString(); }
        catch (Throwable t) { return; }
        pyThread.execute(() -> {
            JSONArray failed = Addons.event(name, js, null);
            if (failed.length() > 0) main.post(() -> reportAddonFailures(failed));
        });
    }

    private void addonFocusEvents(AccessibilityNodeInfo n) {
        if (!Addons.started() || n == null) return;
        if (lastAddonFocus != null && !lastAddonFocus.equals(n)) addonEvent("loseFocus", lastAddonFocus);
        addonEvent("gainFocus", n);
        addonEvent("becomeNavigatorObject", n);
        lastAddonFocus = n;
    }

    private void addonEventsFor(AccessibilityEvent e) {
        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = e.getPackageName();
            if (pkg != null && !pkg.toString().equals(lastForegroundPkg)) {
                lastForegroundPkg = pkg.toString();
                if (Addons.wants("foreground")) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    addonEvent("foreground", root != null ? root : e.getSource());
                }
            }
        } else if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (Addons.wants("valueChange")) addonEvent("valueChange", e.getSource());
        } else if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (Addons.wants("stateChange")) {
                AccessibilityNodeInfo src = e.getSource();
                if (src != null && src.isCheckable()) addonEvent("stateChange", src);
            }
        } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && Build.VERSION.SDK_INT >= 30) {
            int types = e.getContentChangeTypes();
            boolean state = (types & AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) != 0;
            boolean name = (types & AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) != 0;
            if (!state && !name) return;
            if (!Addons.wants("stateChange") && !Addons.wants("valueChange") && !Addons.wants("nameChange")) return;
            AccessibilityNodeInfo src = e.getSource();
            if (src == null || !src.equals(navigator)) return;
            if (state) addonEvent(src.getRangeInfo() != null ? "valueChange" : "stateChange", src);
            if (name) addonEvent("nameChange", src);
        }
    }

    private void speakRaw(String text, boolean interrupt) {
        if (speech != null) speech.speak(text, interrupt, null, false);
    }

    private String m(String key, String fallback) { return NvdaPy.msg(key, fallback); }

    private String describe(AccessibilityNodeInfo n, String reason) {
        if (n == null) return "";
        if (NvdaPy.ready()) {
            String s = NvdaPy.describe(Nodes.toJson(n).toString(), reason);
            if (s != null) return s;
        }
        return Nodes.fallbackDescribe(n);
    }

    /** NVDA-style focus announcement: containers left, containers entered, then the object. */
    private void announce(AccessibilityNodeInfo n, boolean interrupt, String reason) {
        if (n == null) return;
        if (holdGuardActive() && n.equals(holdGuardNode)) return;
        List<AccessibilityNodeInfo> now = Prefs.reportContainers(this) ? Nodes.containers(n) : new ArrayList<>();
        List<AccessibilityNodeInfo> entered = new ArrayList<>(), exited = new ArrayList<>();
        for (AccessibilityNodeInfo c : now) if (!lastContainers.contains(c)) entered.add(c);
        for (int i = lastContainers.size() - 1; i >= 0; i--) {
            AccessibilityNodeInfo c = lastContainers.get(i);
            if (!now.contains(c)) exited.add(c);
        }
        lastContainers = now;
        String text = null;
        if (NvdaPy.ready()) {
            text = NvdaPy.describeFocus(Nodes.toJson(n).toString(),
                    Nodes.containersJson(entered).toString(), Nodes.containersJson(exited).toString(), reason);
        }
        if (text == null) text = Nodes.fallbackDescribe(n);
        speak(text, interrupt);
        if ("focus".equals(reason)) addonFocusEvents(n);
    }

    private void setNavigator(AccessibilityNodeInfo n, boolean speakIt, boolean interrupt) {
        if (n == null) return;
        navigator = n;
        reviewText = textFor(n);
        reviewPos = 0;
        if (!n.isAccessibilityFocused()) {
            ownFocusTarget = n;
            n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        }
        if (speakIt) announce(n, interrupt, "focus");
    }

    private String textFor(AccessibilityNodeInfo n) {
        if (n == null) return "";
        String t = Nodes.str(n.getText());
        if (t.isEmpty()) t = Nodes.name(n);
        return t;
    }

    // ------------------------------------------------------------------ events

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        try {
            handleEvent(e);
        } catch (Throwable t) {
            Log.e(TAG, "event " + e.getEventType(), t);
        }
    }

    private void handleEvent(AccessibilityEvent e) {
        if (speech == null) return;
        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null
                && !"com.android.systemui".contentEquals(e.getPackageName())) {
            activePkg = e.getPackageName().toString();
        }
        if (sleeping()) return;
        if (Addons.started()) {
            try { addonEventsFor(e); } catch (Throwable t) { Log.w(TAG, "add-on events", t); }
        }
        switch (type) {
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START: {
                touchExploring = false;
                gestureInProgress = false;
                if (sayAllActive) stopSayAll();
                touchFirstNode = null;
                long now = SystemClock.uptimeMillis();
                if (tapTracker.onStart(now) == TapTracker.Result.DOUBLE_TAP) interruptForTap();
                main.removeCallbacks(holdCheck);
                main.postDelayed(holdCheck, TapTracker.HOLD_MS);
                break;
            }
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START:
                touchExploring = true;
                if (tapTracker.touching()) tapTracker.onMoved();   // exploring by touch is not a tap
                break;
            case AccessibilityEvent.TYPE_GESTURE_DETECTION_START:
                gestureInProgress = true;
                tapTracker.onMoved();   // a swipe never interrupts speech by itself
                break;
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
                // Lift to type on the on-screen keyboard, like TalkBack.
                if (touchExploring && !gestureInProgress && lastHoverIme && lastHover != null && Prefs.liftToType(this)) {
                    lastHover.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                touchExploring = false;
                tapTracker.onEnd(SystemClock.uptimeMillis());
                main.removeCallbacks(holdCheck);
                touchFirstNode = null;
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                onHover(e);
                break;
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                onFocus(e.getSource());
                break;
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED: {
                AccessibilityNodeInfo src = e.getSource();
                if (src == null) break;
                if (src.equals(ownFocusTarget) || src.equals(navigator)) { ownFocusTarget = null; break; }
                // Something else (an app, or the system) moved accessibility focus: follow it, like NVDA's navigator follows focus.
                navigator = src;
                reviewText = textFor(src);
                reviewPos = 0;
                announce(src, true, "focus");
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                if (e.getContentChangeTypes() == 0 || Build.VERSION.SDK_INT < 28
                        || (e.getContentChangeTypes() & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED) != 0) {
                    main.removeCallbacks(windowChanged);
                    main.postDelayed(windowChanged, 250);
                }
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                onTextChanged(e);
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                onSelectionChanged(e);
                break;
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                onClicked(e.getSource());
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                onContentChanged(e);
                break;
            case AccessibilityEvent.TYPE_ANNOUNCEMENT:
                if (holdGuardActive() && holdGuardNode != null && joinText(e).equals(Nodes.name(holdGuardNode))) break;
                speak(joinText(e), false);
                break;
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                boolean isToast = !(e.getParcelableData() instanceof Notification);
                String t = joinText(e);
                if (t.isEmpty()) break;
                if (isToast) speak(t, false);
                else if (Prefs.notifications(this)) speak(t, false);
                break;
            }
            default:
                break;
        }
    }

    private static String joinText(AccessibilityEvent e) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence cs : e.getText()) {
            if (cs == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(cs);
        }
        if (sb.length() == 0 && e.getContentDescription() != null) sb.append(e.getContentDescription());
        return sb.toString().trim();
    }

    private void onHover(AccessibilityEvent e) {
        AccessibilityNodeInfo src = e.getSource();
        AccessibilityNodeInfo stop = Nodes.stopFor(src);
        if (stop == null) return;
        if (tapTracker.touching()) {
            if (touchFirstNode == null) touchFirstNode = stop;
            else if (!touchFirstNode.equals(stop)) tapTracker.onMoved();
        }
        if (holdGuardActive() && stop.equals(holdGuardNode)) return;
        lastHover = stop;
        lastHoverIme = false;
        AccessibilityWindowInfo w = stop.getWindow();
        if (w != null && w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) lastHoverIme = true;
        if (stop.equals(navigator)) return;
        setNavigator(stop, true, true);
    }

    private void onFocus(AccessibilityNodeInfo src) {
        if (src == null) return;
        focus = src;
        boolean editable = src.isEditable();
        // NVDA's automatic focus mode: entering an edit field plays the focus mode sound, leaving it the browse mode sound.
        if (Prefs.modeSounds(this)) {
            if (editable && !inEditField) Sounds.play("focusMode");
            else if (!editable && inEditField) Sounds.play("browseMode");
        }
        inEditField = editable;
        if (editable) {
            lastSelNode = src;
            lastSelStart = src.getTextSelectionStart();
            lastSelEnd = src.getTextSelectionEnd();
        }
        if (src.equals(navigator)) return;
        main.removeCallbacks(windowChanged);
        setNavigator(src, true, true);
    }

    private void onWindowSettled() {
        if (speech == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        if (navigator != null && navigator.refresh() && navigator.getWindowId() == root.getWindowId()
                && navigator.isVisibleToUser()) {
            return;
        }
        // v0.2 PIN fix: the lock screen refreshes slowly and in several windows. While it is up, keep
        // the navigator on the key it is on, so typing a PIN is not interrupted by a re-read of the screen.
        if (isLocked() && navigator != null && navigator.refresh() && navigator.isVisibleToUser()) return;
        lastContainers = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        String title = windowTitle();
        if (!title.isEmpty()) sb.append(title);
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null || !target.isVisibleToUser()) {
            List<AccessibilityNodeInfo> stops = Nodes.stops(root);
            target = stops.isEmpty() ? null : stops.get(0);
        }
        if (target != null) {
            navigator = target;
            reviewText = textFor(target);
            reviewPos = 0;
            ownFocusTarget = target;
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            lastContainers = Nodes.containers(target);
            String d = describe(target, "focus");
            if (!d.isEmpty() && !d.equals(title)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(d);
            }
        }
        if (sb.length() > 0) speak(sb.toString(), true);
    }

    private String windowTitle() {
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.isActive()) {
                    CharSequence t = Build.VERSION.SDK_INT >= 24 ? w.getTitle() : null;
                    if (t != null && t.length() > 0) return t.toString();
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r != null && r.getPackageName() != null) return appLabel(r.getPackageName().toString());
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void onTextChanged(AccessibilityEvent e) {
        lastTextChange = SystemClock.uptimeMillis();
        List<CharSequence> tl = e.getText();
        String text = tl.isEmpty() || tl.get(0) == null ? "" : tl.get(0).toString();
        String before = e.getBeforeText() == null ? "" : e.getBeforeText().toString();
        int from = e.getFromIndex(), added = e.getAddedCount(), removed = e.getRemovedCount();
        if (from < 0) return;
        if (e.isPassword()) {
            lastPasswordEvent = SystemClock.uptimeMillis();
            // The lock screen's PIN view sends a second event (1 removed, 1 added) when a digit it showed
            // turns into a dot; that is not a new character, so only plain additions speak.
            // If the fallback star already spoke for this key press, don't speak a second one.
            if (pinStarSpokenAt > 0 && lastPasswordEvent - pinStarSpokenAt < 1500) { pinStarSpokenAt = 0; return; }
            if (added > 0 && removed == 0 && Prefs.typedChars(this)) speakStar();
            return;
        }
        if (Prefs.interruptTyped(this) && added > 0 && speech != null && !sayAllActive) speech.cancel();
        if (added == 1 && removed == 0 && from < text.length()) {
            char c = text.charAt(from);
            boolean wordEnd = !Character.isLetterOrDigit(c);
            if (wordEnd && Prefs.typedWords(this)) {
                int s = from;
                while (s > 0 && Character.isLetterOrDigit(text.charAt(s - 1))) s--;
                String word = text.substring(s, from);
                if (!word.isEmpty()) {
                    speakRaw(word, true);
                    if (!Prefs.typedChars(this)) return;
                    if (Prefs.typedChars(this)) { speakCharQueued(String.valueOf(c)); return; }
                }
            }
            if (Prefs.typedChars(this)) speakChar(String.valueOf(c));
        } else if (added == 0 && removed > 0 && from + removed <= before.length()) {
            String gone = before.substring(from, from + removed);
            if (removed == 1) speakChar(gone);
            else speak(gone, true);
        } else if (added > 1 && from + added <= text.length()) {
            String add = text.substring(from, from + added);
            if (add.length() <= 300) speak(add, true);
        }
    }

    private void speakChar(String c) {
        String s = NvdaPy.ready() ? NvdaPy.speakCharacter(c) : c;
        if (speech != null) speech.speakCharacter(c, s, true); else speakRaw(s == null ? c : s, true);
    }

    /** NVDA's typed-character echo for a password character, without a Python call per key. */
    private void speakStar() {
        if (starText == null && NvdaPy.ready()) starText = NvdaPy.speakCharacter("*");
        String st = starText == null ? "*" : starText;
        if (speech != null) speech.speakCharacter("*", st, true); else speakRaw(st, true);
    }

    private void speakCharQueued(String c) {
        String s = NvdaPy.ready() ? NvdaPy.speakCharacter(c) : c;
        if (speech != null) speech.speakCharacter(c, s, false); else speakRaw(s == null ? c : s, false);
    }

    private void onSelectionChanged(AccessibilityEvent e) {
        AccessibilityNodeInfo src = e.getSource();
        int start = e.getFromIndex(), end = e.getToIndex();
        List<CharSequence> tl = e.getText();
        String text = tl.isEmpty() || tl.get(0) == null ? "" : tl.get(0).toString();
        boolean sameNode = src != null && src.equals(lastSelNode);
        int oldStart = lastSelStart, oldEnd = lastSelEnd;
        lastSelNode = src;
        lastSelStart = start;
        lastSelEnd = end;
        if (SystemClock.uptimeMillis() - lastTextChange < 150) return;
        if (!sameNode || start < 0 || e.isPassword()) return;
        if (start == end) {
            if (oldStart == oldEnd) {
                int delta = start - oldStart;
                if (delta == 0) return;
                if (Math.abs(delta) == 1) {
                    // NVDA speaks the character at the caret after moving by character.
                    if (start < text.length()) speakChar(String.valueOf(text.charAt(start)));
                    else speakRaw(m("blank", "blank"), true);
                } else {
                    speak(lineAt(text, start), true);
                }
            } else {
                // A selection was removed.
                int a = Math.min(oldStart, oldEnd), b = Math.max(oldStart, oldEnd);
                if (a >= 0 && b <= text.length() && b > a) {
                    speak(String.format(m("unselected", "unselected %s"), text.substring(a, b)), true);
                }
            }
            return;
        }
        int a = Math.min(start, end), b = Math.max(start, end);
        int oa = Math.min(oldStart, oldEnd), ob = Math.max(oldStart, oldEnd);
        if (b > text.length()) return;
        if (oa >= 0 && a == oa && b > ob && ob >= a) {
            speak(String.format(m("selected", "selected %s"), text.substring(ob, b)), true);
        } else if (oa >= 0 && b == ob && a < oa && oa <= b) {
            speak(String.format(m("selected", "selected %s"), text.substring(a, oa)), true);
        } else if (oa >= 0 && a == oa && b < ob && ob <= text.length()) {
            speak(String.format(m("unselected", "unselected %s"), text.substring(b, ob)), true);
        } else if (oa >= 0 && b == ob && a > oa) {
            speak(String.format(m("unselected", "unselected %s"), text.substring(oa, a)), true);
        } else {
            speak(String.format(m("selected", "selected %s"), text.substring(a, b)), true);
        }
    }

    private static String lineAt(String text, int pos) {
        if (text.isEmpty()) return "";
        pos = Math.max(0, Math.min(pos, text.length()));
        int s = text.lastIndexOf('\n', Math.max(0, pos - 1));
        if (pos > 0 && pos <= text.length() && text.charAt(pos - 1) == '\n') s = pos - 1;
        int e2 = text.indexOf('\n', pos);
        String line = text.substring(s + 1, e2 < 0 ? text.length() : e2);
        return line;
    }

    private void onClicked(AccessibilityNodeInfo src) {
        if (src == null) return;
        if (src.isCheckable() || Nodes.hasAction(src, AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
                || Nodes.hasAction(src, AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE)) {
            main.postDelayed(() -> speakStateChange(src), 120);
        }
    }

    private void speakStateChange(AccessibilityNodeInfo n) {
        if (n == null || !n.refresh()) return;
        String s = NvdaPy.ready() ? NvdaPy.stateSpeech(Nodes.toJson(n).toString()) : (n.isChecked() ? "checked" : "not checked");
        if (s == null || s.isEmpty()) return;
        String key = n.hashCode() + "|" + s;
        if (key.equals(lastStateSpoken)) return;
        lastStateSpoken = key;
        speak(s, true);
    }

    private void onContentChanged(AccessibilityEvent e) {
        AccessibilityNodeInfo src = e.getSource();
        if (src == null) return;
        AccessibilityNodeInfo.RangeInfo ri = src.getRangeInfo();
        int types = e.getContentChangeTypes();
        if (ri != null) {
            boolean adjustable = Nodes.hasAction(src, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                    || Nodes.hasAction(src, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            if (src.equals(navigator) || src.isFocused()) {
                if (adjustable) { speakStateChange(src); return; }
            }
            if (!adjustable && Prefs.progressBeeps(this) && src.isVisibleToUser()) {
                double pct = ri.getType() == AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT ? ri.getCurrent()
                        : (ri.getMax() > ri.getMin() ? (ri.getCurrent() - ri.getMin()) * 100.0 / (ri.getMax() - ri.getMin()) : -1);
                int p = (int) Math.round(pct);
                if (p >= 0 && p != lastProgress) {
                    lastProgress = p;
                    // NVDA beeps progress bars: 110 Hz * 2^(percent/25), 40 ms.
                    Tones.progress(p);
                }
            }
            return;
        }
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        if (Build.VERSION.SDK_INT >= 30 && (types & AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) != 0
                && src.equals(navigator)) {
            speakStateChange(src);
            return;
        }
        if (Build.VERSION.SDK_INT >= 28 && (types & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE) != 0) {
            String pane = Nodes.str(src.getPaneTitle());
            if (!pane.isEmpty()) speak(pane, false);
            return;
        }
        // Live regions, like NVDA's ARIA live region support.
        if (Prefs.b(this, Prefs.REPORT_DYNAMIC, true) && src.getLiveRegion() != 0 && (types & (AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)) != 0) {
            String t = Nodes.name(src);
            long now = SystemClock.uptimeMillis();
            if (!t.isEmpty() && (!t.equals(lastLive) || now - lastLiveTime > 2000)) {
                lastLive = t;
                lastLiveTime = now;
                speak(t, src.getLiveRegion() == 2);
            }
        }
    }

    // ------------------------------------------------------------------ navigation

    private AccessibilityNodeInfo navRoot() {
        if (navigator != null && navigator.refresh()) {
            AccessibilityWindowInfo w = navigator.getWindow();
            if (w != null && w.getRoot() != null) return w.getRoot();
        }
        return getRootInActiveWindow();
    }

    /** NVDA's navigatorObject_nextInFlow / previousInFlow (flick right and left in object mode). */
    void moveInFlow(int dir) { moveInFlow(dir, true); }

    void moveInFlow(int dir, boolean allowAutoScroll) {
        AccessibilityNodeInfo root = navRoot();
        if (root == null) { speak(m("noFocus", "No focus")); Sounds.play("error"); return; }
        List<AccessibilityNodeInfo> stops = Nodes.stops(root);
        if (stops.isEmpty()) { speak(m("noFocus", "No focus")); return; }
        int idx = navigator == null ? -1 : stops.indexOf(navigator);
        int next;
        if (idx < 0) next = dir > 0 ? 0 : stops.size() - 1;
        else next = idx + dir;
        if (allowAutoScroll && autoScroll(stops, idx, next, dir)) return;
        if (next < 0 || next >= stops.size()) {
            // At the edge, try to scroll the list the navigator is in, then try again.
            if (idx >= 0 && scrollContaining(stops.get(idx), dir)) {
                main.postDelayed(() -> {
                    AccessibilityNodeInfo r = navRoot();
                    List<AccessibilityNodeInfo> s2 = Nodes.stops(r);
                    int i2 = navigator == null ? -1 : s2.indexOf(navigator);
                    int n2 = i2 + dir;
                    if (i2 >= 0 && n2 >= 0 && n2 < s2.size()) setNavigator(s2.get(n2), true, true);
                    else speak(dir > 0 ? m("noNext", "No next") : m("noPrevious", "No previous"));
                }, 350);
                return;
            }
            speak(dir > 0 ? m("noNext", "No next") : m("noPrevious", "No previous"));
            return;
        }
        setNavigator(stops.get(next), true, true);
    }

    private boolean scrollContaining(AccessibilityNodeInfo n, int dir) {
        AccessibilityNodeInfo p = n;
        for (int i = 0; i < 20 && p != null; i++) {
            if (p.isScrollable()) {
                return p.performAction(dir > 0 ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }
            p = p.getParent();
        }
        return false;
    }

    /** NVDA's navigatorObject_parent (flick up in object mode). */
    void moveToParent() {
        if (navigator == null) { speak(m("noFocus", "No focus")); return; }
        AccessibilityNodeInfo p = navigator.getParent();
        while (p != null && !p.isVisibleToUser()) p = p.getParent();
        if (p == null) { speak(m("noContaining", "No containing object")); return; }
        navigator = p;
        ownFocusTarget = p;
        p.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        reviewText = textFor(p);
        reviewPos = 0;
        String text = NvdaPy.ready() ? NvdaPy.describe(Nodes.containerJson(p).toString(), "query") : "";
        if (text == null || text.isEmpty()) text = describe(p, "query");
        if (Nodes.isReportableContainer(p) || text.isEmpty()) {
            String rt = NvdaPy.ready() ? NvdaPy.describeFocus("{}", new JSONArray().put(Nodes.containerJson(p)).toString(), "[]", "query") : "";
            if (rt != null && !rt.trim().isEmpty()) text = rt.replaceAll(",\\s*$", "");
        }
        speak(text.isEmpty() ? Nodes.simpleClass(p) : text, true);
    }

    /** NVDA's navigatorObject_firstChild (flick down in object mode). */
    void moveToFirstChild() {
        if (navigator == null) { speak(m("noFocus", "No focus")); return; }
        List<AccessibilityNodeInfo> inside = new ArrayList<>();
        for (int i = 0; i < navigator.getChildCount(); i++) {
            List<AccessibilityNodeInfo> s = Nodes.stops(navigator.getChild(i));
            if (!s.isEmpty()) { inside.add(s.get(0)); break; }
        }
        if (inside.isEmpty()) { speak(_noChildren()); return; }
        setNavigator(inside.get(0), true, true);
    }

    private String _noChildren() { return getString(R.string.no_children); }

    /** Moves the review cursor or the caret by character, word or line (text mode). */
    void moveText(int granularity, int dir) {
        AccessibilityNodeInfo n = navigator;
        if (n != null && n.isEditable() && n.isFocused()) {
            Bundle b = new Bundle();
            b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity);
            b.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false);
            boolean ok = n.performAction(dir > 0 ? AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                    : AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, b);
            if (!ok) speak(dir > 0 ? m("bottom", "bottom") : m("top", "top"));
            return;
        }
        if (n != null) {
            n.refresh();
            String t = textFor(n);
            if (!t.equals(reviewText)) { reviewText = t; reviewPos = 0; }
        }
        String t = reviewText;
        if (t.isEmpty()) { speakRaw(m("blank", "blank"), true); return; }
        if (granularity == AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER) {
            int np = reviewPos + dir;
            if (np < 0 || np >= t.length()) { speak(dir > 0 ? m("bottom", "bottom") : m("top", "top")); speakCharQueued(String.valueOf(t.charAt(reviewPos))); return; }
            reviewPos = np;
            speakChar(String.valueOf(t.charAt(reviewPos)));
        } else if (granularity == AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD) {
            int p = reviewPos;
            if (dir > 0) {
                while (p < t.length() && !Character.isWhitespace(t.charAt(p))) p++;
                while (p < t.length() && Character.isWhitespace(t.charAt(p))) p++;
                if (p >= t.length()) { speak(m("bottom", "bottom")); return; }
            } else {
                while (p > 0 && !Character.isWhitespace(t.charAt(p - 1))) p--;
                if (p == 0 && reviewPos == 0) { speak(m("top", "top")); return; }
                p--;
                while (p > 0 && Character.isWhitespace(t.charAt(p))) p--;
                while (p > 0 && !Character.isWhitespace(t.charAt(p - 1))) p--;
                p = Math.max(0, p);
            }
            reviewPos = p;
            int e2 = p;
            while (e2 < t.length() && !Character.isWhitespace(t.charAt(e2))) e2++;
            speak(t.substring(p, e2), true);
        } else {
            int lineStart = t.lastIndexOf('\n', Math.max(0, reviewPos - 1));
            if (reviewPos > 0 && t.charAt(reviewPos - 1) == '\n') lineStart = reviewPos - 1;
            int ls = lineStart + 1;
            int np;
            if (dir > 0) {
                int nl = t.indexOf('\n', reviewPos);
                if (nl < 0) { speak(m("bottom", "bottom")); speak(lineAt(t, reviewPos), false); return; }
                np = nl + 1;
            } else {
                if (ls == 0) { speak(m("top", "top")); speak(lineAt(t, reviewPos), false); return; }
                int prevStart = t.lastIndexOf('\n', ls - 2);
                np = prevStart + 1;
            }
            reviewPos = Math.max(0, Math.min(np, t.length() - 1));
            String line = lineAt(t, reviewPos);
            speak(line.isEmpty() ? m("blank", "blank") : line, true);
        }
    }

    // ------------------------------------------------------------------ say all

    void sayAll() {
        AccessibilityNodeInfo root = navRoot();
        if (root == null) return;
        List<AccessibilityNodeInfo> stops = Nodes.stops(root);
        int idx = navigator == null ? 0 : Math.max(0, stops.indexOf(navigator));
        sayAllList = new ArrayList<>(stops.subList(idx, Math.min(stops.size(), idx + 300)));
        if (sayAllList.isEmpty()) return;
        sayAllActive = true;
        speech.cancel();
        for (int i = 0; i < sayAllList.size(); i++) {
            String d = describe(sayAllList.get(i), "sayall");
            if (d.isEmpty()) d = " ";
            speech.speak(d, false, "sayall-" + i, true);
        }
    }

    private void stopSayAll() {
        if (!sayAllActive) return;
        sayAllActive = false;
        sayAllList = null;
        if (speech != null) speech.cancel();
    }

    private void onUtteranceStart(String id) {
        if (!sayAllActive || id == null || !id.startsWith("sayall-") || sayAllList == null) return;
        try {
            int i = Integer.parseInt(id.substring(7));
            AccessibilityNodeInfo n = sayAllList.get(i);
            navigator = n;
            reviewText = textFor(n);
            reviewPos = 0;
            ownFocusTarget = n;
            n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            lastContainers = Nodes.containers(n);
        } catch (Exception ignored) {}
    }

    private void onUtteranceDone(String id) {
        if (sayAllActive && sayAllList != null && ("sayall-" + (sayAllList.size() - 1)).equals(id)) {
            sayAllActive = false;
        }
    }

    // ------------------------------------------------------------------ commands

    /** A command with NVDA's description, so input help can say what a gesture or key does. */
    private static final class Cmd {
        final String description;
        final Runnable action;
        Cmd(String d, Runnable a) { description = d; action = a; }
    }

    private final Map<String, Cmd> commands = new LinkedHashMap<>();

    private void ensureCommands() {
        if (!commands.isEmpty()) return;
        commands.put("next", new Cmd(getString(R.string.cmd_next), () -> { if (textMode) moveText(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER, 1); else moveInFlow(1); }));
        commands.put("previous", new Cmd(getString(R.string.cmd_previous), () -> { if (textMode) moveText(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER, -1); else moveInFlow(-1); }));
        commands.put("up", new Cmd(getString(R.string.cmd_up), () -> { if (textMode) moveText(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE, -1); else moveToParent(); }));
        commands.put("down", new Cmd(getString(R.string.cmd_down), () -> { if (textMode) moveText(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE, 1); else moveToFirstChild(); }));
        commands.put("nextWord", new Cmd(getString(R.string.cmd_next_word), () -> moveText(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD, 1)));
        commands.put("previousWord", new Cmd(getString(R.string.cmd_previous_word), () -> moveText(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD, -1)));
        commands.put("objNext", new Cmd(getString(R.string.cmd_next), () -> moveInFlow(1)));
        commands.put("objPrevious", new Cmd(getString(R.string.cmd_previous), () -> moveInFlow(-1)));
        commands.put("objParent", new Cmd(getString(R.string.cmd_up), this::moveToParent));
        commands.put("objChild", new Cmd(getString(R.string.cmd_down), this::moveToFirstChild));
        commands.put("menu", new Cmd(getString(R.string.cmd_menu), this::openMenu));
        commands.put("touchMode", new Cmd(getString(R.string.cmd_touch_mode), this::toggleTouchMode));
        commands.put("stop", new Cmd(getString(R.string.cmd_stop), () -> { stopSayAll(); if (speech != null) speech.cancel(); }));
        commands.put("sayAll", new Cmd(getString(R.string.cmd_say_all), this::sayAll));
        commands.put("focus", new Cmd(getString(R.string.cmd_report_focus), this::reportFocus));
        commands.put("current", new Cmd(getString(R.string.cmd_current), () -> {
            // As in NVDA, pressing it twice quickly spells the current item.
            long now = SystemClock.uptimeMillis();
            boolean twice = now - lastCurrentPress < 600;
            lastCurrentPress = twice ? 0 : now;
            if (twice) { spellCurrent(); return; }
            if (navigator != null) { navigator.refresh(); speak(describe(navigator, "query")); }
        }));
        commands.put("title", new Cmd(getString(R.string.cmd_title), () -> speak(windowTitle())));
        commands.put("readWindow", new Cmd(getString(R.string.cmd_read_window), this::readWindow));
        commands.put("activate", new Cmd(getString(R.string.cmd_activate), this::activate));
        commands.put("speechMode", new Cmd(getString(R.string.cmd_speech_mode), this::cycleSpeechMode));
        commands.put("symbolLevel", new Cmd(getString(R.string.cmd_symbol_level), this::cycleSymbolLevel));
        commands.put("typedChars", new Cmd(getString(R.string.cmd_typed_chars), () -> toggleEcho(Prefs.TYPED_CHARS, true, "typedCharsFmt", "Speak typed characters {mode}")));
        commands.put("typedWords", new Cmd(getString(R.string.cmd_typed_words), () -> toggleEcho(Prefs.TYPED_WORDS, false, "typedWordsFmt", "Speak typed words {mode}")));
        commands.put("time", new Cmd(getString(R.string.cmd_time), this::reportTime));
        commands.put("battery", new Cmd(getString(R.string.cmd_battery), this::reportBattery));
        commands.put("inputHelp", new Cmd(getString(R.string.cmd_input_help), this::toggleInputHelp));
        commands.put("passThrough", new Cmd(getString(R.string.cmd_pass_through), this::togglePassThrough));
        commands.put("quit", new Cmd(getString(R.string.cmd_quit), this::quitPressed));
        commands.put("console", new Cmd(getString(R.string.cmd_console), this::openConsole));
        commands.put("speechViewer", new Cmd(getString(R.string.cmd_speech_viewer), () -> openActivity(SpeechViewerActivity.class)));
        commands.put("settings", new Cmd(getString(R.string.cmd_settings), () -> openActivity(SettingsActivity.class)));
        commands.put("back", new Cmd(getString(R.string.cmd_back), () -> performGlobalAction(GLOBAL_ACTION_BACK)));
        commands.put("home", new Cmd(getString(R.string.cmd_home), () -> performGlobalAction(GLOBAL_ACTION_HOME)));
        commands.put("recents", new Cmd(getString(R.string.cmd_recents), () -> performGlobalAction(GLOBAL_ACTION_RECENTS)));
        commands.put("notifications", new Cmd(getString(R.string.cmd_notifications), () -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)));
        commands.put("ringNext", new Cmd(getString(R.string.cmd_ring_next), () -> moveRing(1)));
        commands.put("ringPrevious", new Cmd(getString(R.string.cmd_ring_previous), () -> moveRing(-1)));
        commands.put("ringUp", new Cmd(getString(R.string.cmd_ring_up), () -> adjustRing(1)));
        commands.put("ringDown", new Cmd(getString(R.string.cmd_ring_down), () -> adjustRing(-1)));
        commands.put("spell", new Cmd(getString(R.string.cmd_spell), this::spellCurrent));
        commands.put("clipboard", new Cmd(getString(R.string.cmd_read_clipboard), this::readClipboard));
        commands.put("sleepMode", new Cmd(getString(R.string.cmd_sleep_mode), this::toggleSleepMode));
        // v0.2 commands
        commands.put("clearEdit", new Cmd(getString(R.string.cmd_clear_edit), this::clearEdit));
        commands.put("paste", new Cmd(getString(R.string.cmd_paste), this::pasteCmd));
        commands.put("copy", new Cmd(getString(R.string.cmd_copy), () -> clipAction(AccessibilityNodeInfo.ACTION_COPY, R.string.copied, "copy")));
        commands.put("cut", new Cmd(getString(R.string.cmd_cut), () -> clipAction(AccessibilityNodeInfo.ACTION_CUT, R.string.cut_done, "copy")));
        commands.put("selectAll", new Cmd(getString(R.string.cmd_select_all), this::selectAll));
        commands.put("scrollForward", new Cmd(getString(R.string.cmd_scroll_forward), () -> scrollCmd(1)));
        commands.put("scrollBackward", new Cmd(getString(R.string.cmd_scroll_backward), () -> scrollCmd(-1)));
        commands.put("autoScroll", new Cmd(getString(R.string.cmd_auto_scroll), () -> toggle(Prefs.AUTO_SCROLL, true, R.string.auto_scroll_on, R.string.auto_scroll_off)));
        commands.put("tapInterrupt", new Cmd(getString(R.string.cmd_tap_interrupt), () -> toggle(Prefs.TAP_INTERRUPT, true, R.string.tap_interrupt_on, R.string.tap_interrupt_off)));
        commands.put("sayCap", new Cmd(getString(R.string.cmd_say_cap), () -> toggle(Prefs.SAY_CAP, false, R.string.say_cap_on, R.string.say_cap_off)));
        commands.put("inputGestures", new Cmd(getString(R.string.cmd_input_gestures), () -> openActivity(InputGesturesActivity.class)));
        commands.put("addonStore", new Cmd(getString(R.string.cmd_addon_store), () -> openActivity(AddonStoreActivity.class)));
        commands.put("quickSettings", new Cmd(getString(R.string.cmd_quick_settings), () -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)));
    }

    private void run(String id) {
        ensureCommands();
        Cmd c = commands.get(id);
        if (c == null) return;
        if (inputHelp && !"inputHelp".equals(id)) {
            speak(c.description);
            return;
        }
        try {
            c.action.run();
        } catch (Throwable t) {
            Log.e(TAG, "command " + id, t);
            Sounds.play("error");
        }
    }

    public void runCommand(String id) { main.post(() -> run(id)); }

    /** NVDA's description of a command, for Input gestures; null when unknown. */
    public String commandDescription(String id) {
        ensureCommands();
        Cmd c = commands.get(id);
        return c == null ? null : c.description;
    }

    /** Input gestures saved a change: read the gesture map again on the next gesture. */
    public void reloadGestures() { main.post(() -> gestureOverrides = null); }

    private void reportFocus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo f = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) f = navigator;
        if (f == null) { speak(m("noFocus", "No focus")); return; }
        speak(describe(f, "query"));
    }

    private void readWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        StringBuilder sb = new StringBuilder(windowTitle());
        for (AccessibilityNodeInfo n : Nodes.stops(root)) {
            String d = describe(n, "sayall");
            if (!d.isEmpty()) sb.append(", ").append(d);
            if (sb.length() > 4000) break;
        }
        speak(sb.toString());
    }

    private void activate() {
        AccessibilityNodeInfo n = navigator;
        if (n == null) return;
        if (isLocked() && isPinKey(n)) {
            // v0.2 PIN fix: the lock screen's PIN view only reports typed digits while it has focus,
            // and on the lock screen it usually does not, so a typed digit was silent. When no password
            // event follows the press, speak NVDA's star for it.
            // The press itself is never delayed or repeated: the click below runs at once, and this
            // only schedules a light check that speaks at most one star per press.
            final long pressed = SystemClock.uptimeMillis();
            main.postDelayed(() -> {
                if (lastPasswordEvent < pressed && Prefs.typedChars(this)) {
                    pinStarSpokenAt = SystemClock.uptimeMillis();
                    speakStar();
                }
            }, 300);
        }
        if (!n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AccessibilityNodeInfo p = n.getParent();
            for (int i = 0; i < 5 && p != null; i++) {
                if (p.isClickable() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                p = p.getParent();
            }
            Sounds.play("error");
        }
    }

    private void toggleTouchMode() {
        textMode = !textMode;
        if (textMode && navigator != null) { reviewText = textFor(navigator); reviewPos = 0; }
        speak(textMode ? m("textMode", "text mode") : m("objectMode", "object mode"));
    }

    private void cycleSpeechMode() {
        String cur = Prefs.speechMode(this);
        String next = "talk".equals(cur) ? "beeps" : "beeps".equals(cur) ? "off" : "talk";
        Prefs.get(this).edit().putString(Prefs.SPEECH_MODE, next).apply();
        String label = m("mode" + Character.toUpperCase(next.charAt(0)) + next.substring(1), next);
        String msg = m("speechModeFmt", "Speech mode {mode}").replace("{mode}", label);
        // NVDA always speaks the speech mode change, even when switching to off or beeps.
        if (speech != null) {
            Prefs.get(this).edit().putString(Prefs.SPEECH_MODE, "talk").commit();
            speech.speak(msg, true, null, false);
            Prefs.get(this).edit().putString(Prefs.SPEECH_MODE, next).apply();
        }
    }

    private void cycleSymbolLevel() {
        int[] levels = {0, 100, 200, 300};
        int cur = Prefs.symbolLevel(this), idx = 0;
        for (int i = 0; i < levels.length; i++) if (levels[i] == cur) idx = i;
        int next = levels[(idx + 1) % levels.length];
        Prefs.get(this).edit().putInt(Prefs.SYMBOL_LEVEL, next).apply();
        NvdaPy.applyConfig(this);
        speakRaw(String.format(m("symbolLevelFmt", "Symbol level %s"), m("symbolLevel" + next, String.valueOf(next))), true);
    }

    private void toggleEcho(String key, boolean def, String fmtKey, String fallback) {
        boolean v = !Prefs.b(this, key, def);
        Prefs.get(this).edit().putBoolean(key, v).apply();
        NvdaPy.applyConfig(this);
        String mode = v ? m("echoAlways", "Always") : m("echoOff", "Off");
        speakRaw(m(fmtKey, fallback).replace("{mode}", mode), true);
    }

    private void reportTime() {
        long now = SystemClock.uptimeMillis();
        Date d = new Date();
        if (now - lastTimePress < 600) {
            speakRaw(DateFormat.getDateInstance(DateFormat.FULL).format(d), true);
        } else {
            speakRaw(android.text.format.DateFormat.getTimeFormat(this).format(d), true);
        }
        lastTimePress = now;
    }

    private void reportBattery() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) { speak(m("noBattery", "No system battery")); return; }
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int pct = scale > 0 ? Math.round(level * 100f / scale) : level;
        String s = String.format(m("percentFmt", "%d percent"), pct) + ", " + (plugged != 0 ? m("plugged", "Plugged in") : m("unplugged", "Unplugged"));
        speakRaw(s, true);
    }

    private void toggleInputHelp() {
        inputHelp = !inputHelp;
        speakRaw(inputHelp ? m("inputHelpOn", "input help on").replace("{gestureKeys}", "NVDA+1") : m("inputHelpOff", "input help off"), true);
    }

    private void togglePassThrough() {
        passThrough = !passThrough;
        if (Prefs.modeSounds(this)) Sounds.play(passThrough ? "focusMode" : "browseMode");
        else speakRaw(passThrough ? m("focusMode", "focus mode") : m("browseMode", "browse mode"), true);
    }

    private void quitPressed() {
        long now = SystemClock.uptimeMillis();
        if (now - lastQuitPress < 2000) {
            exitScreenReader(false);
        } else {
            speakRaw(getString(R.string.press_again_to_exit), true);
        }
        lastQuitPress = now;
    }

    private long lastCurrentPress = 0;

    // ---- v0.2: read clipboard and sleep mode ----------------------------------------------------

    /** Android only lets the app in front read the clipboard, so a see-through screen reads it. */
    private void readClipboard() {
        Intent i = new Intent(this, ClipboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try { startActivity(i); } catch (Throwable t) { speakRaw(m("clipboardEmpty", "There is no text on the clipboard"), true); }
    }

    /** Called by ClipboardActivity with the clipboard text, or null when there is none. */
    void reportClipboard(CharSequence text) {
        String s = text == null ? "" : text.toString();
        if (s.trim().isEmpty()) { speakRaw(m("clipboardEmpty", "There is no text on the clipboard"), true); return; }
        if (s.length() > 1024) {
            speakRaw(m("clipboardLargeFmt", "The clipboard contains a large amount of text. It is %s character long").replace("%s", String.valueOf(s.length())), true);
            return;
        }
        speakRaw(s, true);
    }

    private final java.util.Set<String> sleepingApps = new java.util.HashSet<>();
    private String activePkg = "";

    /** NVDA's sleep mode, per app: Nvidiator stays quiet in that app. Gestures still work so you
     *  can turn it off again (Android can't hand them to the app while the screen reader is on). */
    private void toggleSleepMode() {
        String pkg = activePkg;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) pkg = root.getPackageName().toString();
        if (pkg == null || pkg.isEmpty() || pkg.equals(getPackageName())) return;
        boolean on = !sleepingApps.contains(pkg);
        if (on) sleepingApps.add(pkg); else sleepingApps.remove(pkg);
        activePkg = pkg;
        speakDirect(m(on ? "sleepOn" : "sleepOff", on ? "Sleep mode on" : "Sleep mode off"), true);
    }

    private boolean sleeping() { return !sleepingApps.isEmpty() && sleepingApps.contains(activePkg); }

    private void spellCurrent() {
        String t = navigator == null ? "" : textFor(navigator);
        if (t.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(t.length(), 200); i++) {
            String c = NvdaPy.ready() ? NvdaPy.speakCharacter(String.valueOf(t.charAt(i))) : String.valueOf(t.charAt(i));
            if (sb.length() > 0) sb.append(", ");
            sb.append(c);
        }
        speakRaw(sb.toString(), true);
    }

    // NVDA's synth settings ring (NVDA+Ctrl+arrows): rate, pitch, volume.
    private static final String[] RING = {Prefs.RATE, Prefs.PITCH, Prefs.VOLUME};

    private String ringLabel(int i) {
        return getString(new int[]{R.string.ring_rate, R.string.ring_pitch, R.string.ring_volume}[i]);
    }

    private void moveRing(int dir) {
        settingsRingIndex = (settingsRingIndex + dir + RING.length) % RING.length;
        String k = RING[settingsRingIndex];
        speakRaw(ringLabel(settingsRingIndex) + " " + Prefs.i(this, k, k.equals(Prefs.VOLUME) ? 100 : 50), true);
    }

    private void adjustRing(int dir) {
        String k = RING[settingsRingIndex];
        int v = Math.max(0, Math.min(100, Prefs.i(this, k, k.equals(Prefs.VOLUME) ? 100 : 50) + dir * 5));
        Prefs.get(this).edit().putInt(k, v).apply();
        if (speech != null) speech.applyVoice();
        speakRaw(String.valueOf(v), true);
    }


    // ------------------------------------------------------------------ v0.2 helpers

    /** Double tap, or double tap and hold: stop speech now, and don't re-read the item under the finger. */
    private void interruptForTap() {
        if (!Prefs.tapInterrupt(this)) return;
        stopSayAll();
        if (speech != null) speech.cancel();
        holdGuardNode = navigator;
        holdGuardUntil = SystemClock.uptimeMillis() + 2500;
    }

    private boolean holdGuardActive() {
        if (holdGuardUntil == 0) return false;
        if (SystemClock.uptimeMillis() > holdGuardUntil) { holdGuardUntil = 0; holdGuardNode = null; return false; }
        return true;
    }

    private boolean isLocked() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    /** A digit key on the lock screen's PIN pad: its label starts with a digit (\"2 ABC\"), or its id is keyN. */
    private static boolean isPinKey(AccessibilityNodeInfo n) {
        String id = n.getViewIdResourceName();
        if (id != null && id.matches(".*:id/key[0-9]$")) return true;
        String label = Nodes.name(n);
        return label != null && !label.isEmpty() && Character.isDigit(label.charAt(0)) && label.length() <= 12;
    }

    private void registerV02Receivers() {
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { onScreenEvent(i.getAction()); }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try { registerReceiver(screenReceiver, f); } catch (Throwable t) { Log.w(TAG, "screen receiver", t); screenReceiver = null; }
        // Connected and disconnected sounds for audio devices (headphones, Bluetooth, USB). No permission needed.
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            audioDevices = new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d) {
                    // The callback first reports every device already there; skip that.
                    if (!audioDevicesPrimed) { audioDevicesPrimed = true; return; }
                    if (hasUserDevice(d) && Prefs.connectionSounds(NvidiatorService.this)) Sounds.play("connected");
                }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) {
                    if (hasUserDevice(d) && Prefs.connectionSounds(NvidiatorService.this)) Sounds.play("disconnected");
                }
            };
            am.registerAudioDeviceCallback(audioDevices, main);
        }
    }

    private static boolean hasUserDevice(AudioDeviceInfo[] d) {
        for (AudioDeviceInfo i : d) {
            int t = i.getType();
            if (t != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && t != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    && t != AudioDeviceInfo.TYPE_BUILTIN_MIC && t != AudioDeviceInfo.TYPE_TELEPHONY
                    && t != AudioDeviceInfo.TYPE_UNKNOWN && i.isSink()) return true;
        }
        return false;
    }

    private void unregisterV02Receivers() {
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Throwable ignored) {}
            screenReceiver = null;
        }
        if (audioDevices != null) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.unregisterAudioDeviceCallback(audioDevices);
            audioDevices = null;
        }
    }

    /** v0.2 screen locked bug: announce lock and unlock, and drop the stale navigator. */
    private void onScreenEvent(String action) {
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_SCREEN_OFF:
                stopSayAll();
                if (speech != null) speech.cancel();
                navigator = null;
                lastContainers = new ArrayList<>();
                if (Prefs.lockAnnounce(this)) speak(getString(R.string.screen_off), true);
                break;
            case Intent.ACTION_SCREEN_ON:
                navigator = null;
                if (Prefs.lockAnnounce(this) && isLocked()) speak(getString(R.string.screen_locked), true);
                main.removeCallbacks(windowChanged);
                main.postDelayed(windowChanged, 600);
                break;
            case Intent.ACTION_USER_PRESENT:
                navigator = null;
                if (Prefs.lockAnnounce(this)) speak(getString(R.string.screen_unlocked), true);
                main.removeCallbacks(windowChanged);
                main.postDelayed(windowChanged, 600);
                break;
            case Intent.ACTION_POWER_CONNECTED:
                if (Prefs.connectionSounds(this)) Sounds.play("connected");
                break;
            case Intent.ACTION_POWER_DISCONNECTED:
                if (Prefs.connectionSounds(this)) Sounds.play("disconnected");
                break;
            default: break;
        }
    }

    private void reportAddonFailures(JSONArray failed) {
        if (failed == null) return;
        for (int i = 0; i < failed.length(); i++) {
            org.json.JSONObject o = failed.optJSONObject(i);
            if (o != null) reportAddonFailure(o.optString("summary"), o.optString("reason"));
        }
    }

    private void reportAddonFailure(String summary, String reason) {
        Sounds.play("error");
        speak(getString(R.string.addon_turned_off, summary, reason == null ? "" : reason), false);
    }

    /** Reloads add-ons after changes in the Add-on Store (NVDA restarts itself for this). */
    static void restartAddons() {
        NvidiatorService s = instance;
        if (s == null) return;
        s.pyThread.execute(() -> {
            JSONArray failed = Addons.restart();
            s.main.post(() -> { s.speak(s.getString(R.string.addons_reloaded), false); s.reportAddonFailures(failed); });
        });
    }

    private AccessibilityNodeInfo focusedEditable() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return f != null && f.isEditable() ? f : null;
    }

    /** Swipe left then right: clears the edit box; elsewhere reports the current item as before. */
    private void clearEdit() {
        AccessibilityNodeInfo e = focusedEditable();
        if (e == null) { run("current"); return; }
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
        if (e.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) speak(getString(R.string.cleared), true);
        else Sounds.play("error");
    }

    /** Swipe right then left: pastes in an edit box; elsewhere moves to the focus as before. */
    private void pasteCmd() {
        AccessibilityNodeInfo e = focusedEditable();
        if (e == null) { run("focus"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) { speak(getString(R.string.nothing_to_paste), true); return; }
        if (e.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            if (Prefs.clipboardSounds(this)) Sounds.play("paste");
            else speak(getString(R.string.pasted), true);
        } else Sounds.play("error");
    }

    private void clipAction(int action, int msg, String sound) {
        AccessibilityNodeInfo e = focusedEditable();
        if (e == null) e = navigator;
        if (e != null && e.performAction(action)) {
            if (sound != null && Prefs.clipboardSounds(this)) Sounds.play(sound);
            else speak(getString(msg), true);
        } else Sounds.play("error");
    }

    private void selectAll() {
        AccessibilityNodeInfo e = focusedEditable();
        if (e == null) { Sounds.play("error"); return; }
        CharSequence t = e.getText();
        Bundle b = new Bundle();
        b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
        b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, t == null ? 0 : t.length());
        if (e.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, b)) speak(getString(R.string.selected_all), true);
        else Sounds.play("error");
    }

    private void scrollCmd(int dir) {
        AccessibilityNodeInfo n = navigator;
        if (n == null) {
            AccessibilityNodeInfo r = navRoot();
            List<AccessibilityNodeInfo> st = r == null ? new ArrayList<>() : Nodes.stops(r);
            n = st.isEmpty() ? null : st.get(0);
        }
        if (n == null || !scrollContaining(n, dir)) speak(getString(R.string.cannot_scroll), true);
    }

    private void toggle(String key, boolean def, int on, int off) {
        boolean v = !Prefs.b(this, key, def);
        Prefs.get(this).edit().putBoolean(key, v).apply();
        speak(getString(v ? on : off), true);
    }

    void openActivity(Class<?> cls) {
        Intent i = new Intent(this, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { startActivity(i); } catch (Throwable t) { Log.w(TAG, "open " + cls.getSimpleName(), t); Sounds.play("error"); }
    }

    private static AccessibilityNodeInfo scrollableAncestor(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo p = n == null ? null : n.getParent();
        for (int i = 0; i < 20 && p != null; i++) {
            if (p.isScrollable()) return p;
            p = p.getParent();
        }
        return null;
    }

    private static boolean isInside(AccessibilityNodeInfo n, AccessibilityNodeInfo ancestor) {
        AccessibilityNodeInfo p = n;
        for (int i = 0; i < 30 && p != null; i++) {
            if (p.equals(ancestor)) return true;
            p = p.getParent();
        }
        return false;
    }

    private static String signature(AccessibilityNodeInfo n) {
        return Nodes.simpleClass(n) + "|" + Nodes.name(n);
    }

    /** v0.2 automatic scrolling: the list's own items, in reading order. */
    private List<AccessibilityNodeInfo> itemsIn(AccessibilityNodeInfo list) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        AccessibilityNodeInfo r = navRoot();
        if (r == null || list == null) return out;
        for (AccessibilityNodeInfo s : Nodes.stops(r)) if (isInside(s, list)) out.add(s);
        return out;
    }

    /**
     * v0.2 automatic scrolling. Returns true when it scrolled and will move the navigator itself:
     * swiping past the last visible item of a list scrolls the list and moves to the first new item.
     */
    private boolean autoScroll(List<AccessibilityNodeInfo> stops, int idx, int next, int dir) {
        if (idx < 0) return false;
        AccessibilityNodeInfo cur = stops.get(idx);
        AccessibilityNodeInfo list = scrollableAncestor(cur);
        if (list == null) return false;
        boolean can = Nodes.hasAction(list, dir > 0 ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        boolean nextInside = next >= 0 && next < stops.size() && isInside(stops.get(next), list);
        if (!FlowLogic.shouldAutoScroll(Prefs.autoScroll(this), true, can, nextInside)) return false;
        List<AccessibilityNodeInfo> beforeNodes = itemsIn(list);
        List<String> before = new ArrayList<>();
        for (AccessibilityNodeInfo b : beforeNodes) before.add(signature(b));
        int navIndex = beforeNodes.indexOf(cur);
        if (!list.performAction(dir > 0 ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return false;
        main.postDelayed(() -> {
            list.refresh();
            List<AccessibilityNodeInfo> afterNodes = itemsIn(list);
            List<String> after = new ArrayList<>();
            for (AccessibilityNodeInfo a : afterNodes) after.add(signature(a));
            int pick = FlowLogic.pickAfterScroll(before, after, navIndex, dir);
            if (pick >= 0 && pick < afterNodes.size()) setNavigator(afterNodes.get(pick), true, true);
            else moveInFlow(dir, false);
        }, 350);
        return true;
    }

    public void pyPlayFile(String path) { main.post(() -> Sounds.playFile(path)); }

    // ------------------------------------------------------------------ gestures

    @Override
    public boolean onGesture(AccessibilityGestureEvent event) {
        if (Build.VERSION.SDK_INT >= 30) return handleGesture(event.getGestureId());
        return false;
    }

    @Override
    protected boolean onGesture(int gestureId) {
        return handleGesture(gestureId);
    }

    private boolean handleGesture(int id) {
        String key = gestureKey(id);
        if (key == null) return false;
        if (sayAllActive && !"swipeDownRight".equals(key)) stopSayAll();
        // Add-on scripts bound to this gesture run first, like NVDA's global plugins.
        Addons.GestureResult ar = Addons.gesture(Addons.tsIdentifier(key), GestureMap.gestureName(key));
        if (ar.failedAddon != null) reportAddonFailure(ar.failedAddon, ar.reason);
        if (ar.handled) return true;
        if (gestureOverrides == null) gestureOverrides = GestureMap.parse(Prefs.s(this, Prefs.GESTURE_MAP, ""));
        String cmd = GestureMap.resolve(key, gestureOverrides);
        // Safety: if the saved bindings somehow leave no NVDA menu gesture, the TalkBack menu gesture opens it.
        if (cmd == null && "swipeDownRight".equals(key) && GestureMap.leavesNoMenu(gestureOverrides)) cmd = "menu";
        if (cmd == null) return false;
        run(cmd);
        return true;
    }


    private static String gestureKey(int id) {
        switch (id) {
            case GESTURE_SWIPE_RIGHT: return "swipeRight";
            case GESTURE_SWIPE_LEFT: return "swipeLeft";
            case GESTURE_SWIPE_UP: return "swipeUp";
            case GESTURE_SWIPE_DOWN: return "swipeDown";
            case GESTURE_SWIPE_LEFT_AND_RIGHT: return "swipeLeftRight";
            case GESTURE_SWIPE_RIGHT_AND_LEFT: return "swipeRightLeft";
            case GESTURE_SWIPE_UP_AND_DOWN: return "swipeUpDown";
            case GESTURE_SWIPE_DOWN_AND_UP: return "swipeDownUp";
            case GESTURE_SWIPE_DOWN_AND_RIGHT: return "swipeDownRight";
            case GESTURE_SWIPE_DOWN_AND_LEFT: return "swipeDownLeft";
            case GESTURE_SWIPE_UP_AND_LEFT: return "swipeUpLeft";
            case GESTURE_SWIPE_UP_AND_RIGHT: return "swipeUpRight";
            case GESTURE_SWIPE_LEFT_AND_UP: return "swipeLeftUp";
            case GESTURE_SWIPE_LEFT_AND_DOWN: return "swipeLeftDown";
            case GESTURE_SWIPE_RIGHT_AND_UP: return "swipeRightUp";
            case GESTURE_SWIPE_RIGHT_AND_DOWN: return "swipeRightDown";
            default: break;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            switch (id) {
                case GESTURE_2_FINGER_SINGLE_TAP: return "2fingerTap";
                case GESTURE_2_FINGER_DOUBLE_TAP: return "2fingerDoubleTap";
                case GESTURE_2_FINGER_TRIPLE_TAP: return "2fingerTripleTap";
                case GESTURE_3_FINGER_SINGLE_TAP: return "3fingerTap";
                case GESTURE_3_FINGER_DOUBLE_TAP: return "3fingerDoubleTap";
                case GESTURE_3_FINGER_TRIPLE_TAP: return "3fingerTripleTap";
                case GESTURE_3_FINGER_SWIPE_UP: return "3fingerSwipeUp";
                case GESTURE_3_FINGER_SWIPE_DOWN: return "3fingerSwipeDown";
                case GESTURE_3_FINGER_SWIPE_LEFT: return "3fingerSwipeLeft";
                case GESTURE_3_FINGER_SWIPE_RIGHT: return "3fingerSwipeRight";
                case GESTURE_4_FINGER_SINGLE_TAP: return "4fingerTap";
                case GESTURE_4_FINGER_DOUBLE_TAP: return "4fingerDoubleTap";
                case GESTURE_4_FINGER_TRIPLE_TAP: return "4fingerTripleTap";
                case GESTURE_4_FINGER_SWIPE_UP: return "4fingerSwipeUp";
                case GESTURE_4_FINGER_SWIPE_DOWN: return "4fingerSwipeDown";
                case GESTURE_4_FINGER_SWIPE_LEFT: return "4fingerSwipeLeft";
                case GESTURE_4_FINGER_SWIPE_RIGHT: return "4fingerSwipeRight";
                default: break;
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            switch (id) {
                case GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD: return "2fingerDoubleTapHold";
                case GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD: return "3fingerDoubleTapHold";
                default: break;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ keyboard

    private boolean isNvdaKey(int code) {
        return (code == KeyEvent.KEYCODE_INSERT && Prefs.keyInsert(this))
                || (code == KeyEvent.KEYCODE_CAPS_LOCK && Prefs.keyCapsLock(this))
                || (code == KeyEvent.KEYCODE_NUMPAD_0 && Prefs.keyInsert(this));
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            return handleKey(event);
        } catch (Throwable t) {
            Log.e(TAG, "key", t);
            return false;
        }
    }

    private boolean handleKey(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        // Never touch the volume keys: the accessibility shortcut (both volume keys) must always work.
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_POWER) return false;
        if (isNvdaKey(code)) {
            if (down) {
                if (ev.getRepeatCount() == 0) { nvdaKeyDown = true; nvdaKeyUsed = false; }
            } else {
                nvdaKeyDown = false;
            }
            return true;
        }
        if ((code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT) && down && !nvdaKeyDown) {
            // NVDA: pressing Control stops speech.
            stopSayAll();
            if (speech != null) speech.cancel();
            return false;
        }
        if (nvdaKeyDown && down && ev.getRepeatCount() == 0 && Addons.started()) {
            String keyName = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").toLowerCase(java.util.Locale.ROOT);
            String ident = "kb:NVDA+" + (ev.isCtrlPressed() ? "control+" : "") + (ev.isShiftPressed() ? "shift+" : "")
                    + (ev.isAltPressed() ? "alt+" : "") + keyName;
            Addons.GestureResult ar = Addons.gesture(ident, ident.substring(3));
            if (ar.failedAddon != null) { final String a = ar.failedAddon, r = ar.reason; main.post(() -> reportAddonFailure(a, r)); }
            if (ar.handled) { nvdaKeyUsed = true; return true; }
        }
        if (nvdaKeyDown) {
            String cmd = nvdaKeyCommand(code, ev.isShiftPressed(), ev.isCtrlPressed());
            if (cmd != null) {
                if (down && ev.getRepeatCount() == 0) {
                    nvdaKeyUsed = true;
                    final String c = cmd;
                    main.post(() -> run(c));
                }
                return true;
            }
            return false;
        }
        // NVDA's single letter navigation, when not typing in an edit field and not in focus mode.
        if (!passThrough && Prefs.quickNav(this) && !ev.isCtrlPressed() && !ev.isAltPressed() && !ev.isMetaPressed()
                && !isEditableFocused()) {
            String role = quickNavRole(code);
            if (role != null) {
                if (down && ev.getRepeatCount() == 0) {
                    final boolean back = ev.isShiftPressed();
                    main.post(() -> quickNav(role, back ? -1 : 1));
                }
                return true;
            }
        }
        return false;
    }

    private boolean isEditableFocused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return f != null && f.isEditable();
    }

    /** NVDA's desktop and laptop layout commands, with Insert or Caps Lock as the NVDA key. */
    private String nvdaKeyCommand(int code, boolean shift, boolean ctrl) {
        if (ctrl) {
            switch (code) {
                case KeyEvent.KEYCODE_Z: return "console";
                case KeyEvent.KEYCODE_G: return "settings";
                case KeyEvent.KEYCODE_DPAD_RIGHT: return "ringNext";
                case KeyEvent.KEYCODE_DPAD_LEFT: return "ringPrevious";
                case KeyEvent.KEYCODE_DPAD_UP: return "ringUp";
                case KeyEvent.KEYCODE_DPAD_DOWN: return "ringDown";
                default: return null;
            }
        }
        if (shift) {
            switch (code) {
                case KeyEvent.KEYCODE_B: return "battery";
                case KeyEvent.KEYCODE_S: return "sleepMode";
                case KeyEvent.KEYCODE_DPAD_RIGHT: return "objNext";
                case KeyEvent.KEYCODE_DPAD_LEFT: return "objPrevious";
                case KeyEvent.KEYCODE_DPAD_UP: return "objParent";
                case KeyEvent.KEYCODE_DPAD_DOWN: return "objChild";
                default: return null;
            }
        }
        switch (code) {
            case KeyEvent.KEYCODE_N: return "menu";
            case KeyEvent.KEYCODE_Q: return "quit";
            case KeyEvent.KEYCODE_S: return "speechMode";
            case KeyEvent.KEYCODE_T: return "title";
            case KeyEvent.KEYCODE_TAB: return "focus";
            case KeyEvent.KEYCODE_B: return "readWindow";
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_A: return "sayAll";
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_L: return "current";
            case KeyEvent.KEYCODE_F12: return "time";
            case KeyEvent.KEYCODE_C: return "clipboard";
            case KeyEvent.KEYCODE_P: return "symbolLevel";
            case KeyEvent.KEYCODE_1: return "inputHelp";
            case KeyEvent.KEYCODE_2: return "typedChars";
            case KeyEvent.KEYCODE_3: return "typedWords";
            case KeyEvent.KEYCODE_SPACE: return "passThrough";
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: return "activate";
            case KeyEvent.KEYCODE_NUMPAD_6: return "objNext";
            case KeyEvent.KEYCODE_NUMPAD_4: return "objPrevious";
            case KeyEvent.KEYCODE_NUMPAD_8: return "objParent";
            case KeyEvent.KEYCODE_NUMPAD_2: return "objChild";
            case KeyEvent.KEYCODE_NUMPAD_5: return "current";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return "activate";
            case KeyEvent.KEYCODE_F1: return "speechViewer";
            default: return null;
        }
    }

    private static String quickNavRole(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_H: return "heading";
            case KeyEvent.KEYCODE_K: return "link";
            case KeyEvent.KEYCODE_B: return "button";
            case KeyEvent.KEYCODE_E: return "edit";
            case KeyEvent.KEYCODE_X: return "checkbox";
            case KeyEvent.KEYCODE_C: return "combobox";
            case KeyEvent.KEYCODE_R: return "radio";
            case KeyEvent.KEYCODE_L: return "list";
            case KeyEvent.KEYCODE_I: return "listitem";
            case KeyEvent.KEYCODE_G: return "graphic";
            case KeyEvent.KEYCODE_D: return "landmark";
            case KeyEvent.KEYCODE_F: return "formfield";
            case KeyEvent.KEYCODE_T: return "table";
            default: return null;
        }
    }

    private boolean matchesQuickNav(String kind, String role, AccessibilityNodeInfo n) {
        switch (kind) {
            case "heading": return role.startsWith("HEADING");
            case "link": return role.equals("LINK");
            case "button": return role.equals("BUTTON") || role.equals("TOGGLEBUTTON");
            case "edit": return role.equals("EDITABLETEXT");
            case "checkbox": return role.equals("CHECKBOX") || role.equals("SWITCH");
            case "combobox": return role.equals("COMBOBOX");
            case "radio": return role.equals("RADIOBUTTON");
            case "listitem": return role.equals("LISTITEM");
            case "graphic": return role.equals("GRAPHIC");
            case "formfield": return n.isEditable() || n.isCheckable() || role.equals("COMBOBOX") || role.equals("SLIDER") || role.equals("BUTTON");
            default: return false;
        }
    }

    private void quickNav(String kind, int dir) {
        AccessibilityNodeInfo root = navRoot();
        if (root == null) return;
        List<AccessibilityNodeInfo> stops = Nodes.stops(root);
        int idx = navigator == null ? -1 : stops.indexOf(navigator);
        boolean containerKind = kind.equals("list") || kind.equals("landmark") || kind.equals("table");
        for (int i = idx + dir; i >= 0 && i < stops.size(); i += dir) {
            AccessibilityNodeInfo n = stops.get(i);
            boolean hit;
            if (containerKind) {
                List<AccessibilityNodeInfo> cs = Nodes.containers(n);
                hit = false;
                for (AccessibilityNodeInfo c : cs) {
                    if (lastContainers.contains(c)) continue;
                    String r = NvdaPy.ready() ? NvdaPy.roleInfo(Nodes.containerJson(c).toString()) : "";
                    if (r == null) r = "";
                    String cr = Nodes.chromeRoleNorm(c);
                    if (kind.equals("list") && (r.equals("LIST") || c.getCollectionInfo() != null)) hit = true;
                    if (kind.equals("table") && r.equals("TABLE")) hit = true;
                    if (kind.equals("landmark") && r.equals("LANDMARK")) hit = true;
                    if (kind.equals("landmark") && cr.equals("navigation")) hit = true;
                }
            } else {
                String role = NvdaPy.ready() ? NvdaPy.roleInfo(Nodes.toJson(n).toString()) : "";
                hit = role != null && matchesQuickNav(kind, role, n);
            }
            if (hit) {
                setNavigator(n, true, true);
                return;
            }
        }
        String roleName = NvdaPy.ready() ? NvdaPy.roleName(quickNavRoleName(kind)) : kind;
        speak(getString(dir > 0 ? R.string.no_next_fmt : R.string.no_previous_fmt, roleName));
    }

    private static String quickNavRoleName(String kind) {
        switch (kind) {
            case "heading": return "HEADING";
            case "link": return "LINK";
            case "button": return "BUTTON";
            case "edit": return "EDITABLETEXT";
            case "checkbox": return "CHECKBOX";
            case "combobox": return "COMBOBOX";
            case "radio": return "RADIOBUTTON";
            case "list": return "LIST";
            case "listitem": return "LISTITEM";
            case "graphic": return "GRAPHIC";
            case "landmark": return "LANDMARK";
            case "table": return "TABLE";
            default: return "UNKNOWN";
        }
    }

    // ------------------------------------------------------------------ menu, exit, TalkBack

    private void takeSnapshot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        snapFg = root;
        snapFocus = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (snapFocus == null) snapFocus = navigator;
        snapNav = navigator;
    }

    void openMenu() {
        takeSnapshot();
        openActivity(NvdaMenuActivity.class);
    }

    void openConsole() {
        takeSnapshot();
        openActivity(PythonConsoleActivity.class);
    }


    /** Turns the screen reader off. With switchToTalkBack and the secure settings permission, TalkBack is turned on in its place. */
    public void exitScreenReader(boolean switchToTalkBack) {
        stopSayAll();
        boolean switched = false;
        if (switchToTalkBack) switched = TalkBackSwitch.toTalkBack(this);
        if (!switched) {
            speakRaw(getString(R.string.exiting_hint), true);
        }
        Sounds.play("exit");
        if (!switched) main.postDelayed(this::disableSelfSafely, 3500);
    }

    boolean isTalkBackOn() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        for (AccessibilityServiceInfo s : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            String id = s.getId();
            if (id != null && id.toLowerCase().contains("talkback")) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Python console API (androidApi.py)

    public AccessibilityNodeInfo pyGetFocus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo f = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return f != null ? f : navigator;
    }

    public AccessibilityNodeInfo pyGetNavigator() { return navigator; }
    public AccessibilityNodeInfo pyGetForeground() { return getRootInActiveWindow(); }

    public AccessibilityNodeInfo pySnapshot(String name) {
        switch (name) {
            case "focus": return snapFocus != null ? snapFocus : pyGetFocus();
            case "nav": return snapNav != null ? snapNav : navigator;
            case "fg": return snapFg != null ? snapFg : getRootInActiveWindow();
            default: return null;
        }
    }

    public boolean pyNavigateTo(AccessibilityNodeInfo n) {
        if (n == null) return false;
        main.post(() -> setNavigator(n, true, true));
        return true;
    }

    public String pyDescribe(AccessibilityNodeInfo n) { return describe(n, "query"); }

    public void pySpeak(String text, boolean interrupt) { main.post(() -> speakDirect(text, interrupt)); }

    public void pySpell(String text) {
        main.post(() -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(200, text.length()); i++) {
                if (sb.length() > 0) sb.append(", ");
                String c = NvdaPy.speakCharacter(String.valueOf(text.charAt(i)));
                sb.append(c == null ? String.valueOf(text.charAt(i)) : c);
            }
            speakRaw(sb.toString(), true);
        });
    }

    public void pyCancelSpeech() { main.post(() -> { if (speech != null) speech.cancel(); }); }
    public String pySpeechMode() { return Prefs.speechMode(this); }
    public void pyBeep(float hz, int length, int left, int right) { Tones.beep(hz, length, left, right); }
    public void pyPlaySound(String name) { main.post(() -> Sounds.play(name)); }

    public String pyGetClip() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return "";
        ClipData c = cm.getPrimaryClip();
        if (c == null || c.getItemCount() == 0) return "";
        CharSequence t = c.getItemAt(0).coerceToText(this);
        return t == null ? "" : t.toString();
    }

    public boolean pyCopyToClip(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return false;
        main.post(() -> cm.setPrimaryClip(ClipData.newPlainText("Nvidiator", text)));
        return true;
    }

    public void reloadSettings() {
        main.post(() -> {
            NvdaPy.applyConfig(this);
            if (speech != null) speech.applyVoice();
        });
    }

    public void restartSpeech() {
        main.post(() -> { if (speech != null) speech.restart(); });
    }

    public void setLanguage(String lang) {
        starText = null;
        pyThread.execute(() -> NvdaPy.setLanguage(this, lang));
    }

    static String flattenServices(String s) { return TextUtils.isEmpty(s) ? "" : s; }

    static ComponentName self(Context c) { return new ComponentName(c, NvidiatorService.class); }

    static boolean hasSecureSettings(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    static String enabledServices(Context c) {
        return flattenServices(Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
    }
}
