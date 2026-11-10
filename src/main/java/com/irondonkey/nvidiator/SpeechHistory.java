// Nvidiator Android: the text behind the speech viewer (NVDA's Tools > Speech viewer).
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import java.util.ArrayList;
import java.util.List;

public final class SpeechHistory {
    public interface Listener { void onSpoken(String text); }

    private static final int MAX = 500;
    private static final List<String> lines = new ArrayList<>();
    private static final List<Listener> listeners = new ArrayList<>();

    private SpeechHistory() {}

    public static synchronized void add(String text) {
        if (text == null || text.trim().isEmpty()) return;
        lines.add(text);
        while (lines.size() > MAX) lines.remove(0);
        for (Listener l : new ArrayList<>(listeners)) l.onSpoken(text);
    }

    public static synchronized List<String> snapshot() { return new ArrayList<>(lines); }

    public static synchronized void clear() { lines.clear(); }

    public static synchronized void addListener(Listener l) { listeners.add(l); }

    public static synchronized void removeListener(Listener l) { listeners.remove(l); }

    public static synchronized boolean hasListeners() { return !listeners.isEmpty(); }
}
