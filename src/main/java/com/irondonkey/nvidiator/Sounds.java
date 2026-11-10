// Nvidiator Android: plays NVDA's own sound files (from NVDA's source/waves folder).
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;

public final class Sounds {
    public static final String[] NAMES = {
            "start", "exit", "browseMode", "focusMode", "error", "textError", "suggestionsOpened", "suggestionsClosed",
            "screenCurtainOn", "screenCurtainOff"};
    private static SoundPool pool;
    private static final Map<String, Integer> ids = new HashMap<>();
    private static final Map<String, Boolean> loaded = new HashMap<>();
    private static final Map<String, Boolean> pending = new HashMap<>();
    private static Context app;
    public static volatile float volume = 1f;

    private Sounds() {}

    public static synchronized void init(Context c) {
        if (pool != null) return;
        app = c.getApplicationContext();
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            synchronized (Sounds.class) {
                for (Map.Entry<String, Integer> e : ids.entrySet()) {
                    if (e.getValue() == sampleId) {
                        loaded.put(e.getKey(), status == 0);
                        if (Boolean.TRUE.equals(pending.remove(e.getKey())) && status == 0) {
                            p.play(sampleId, volume, volume, 1, 0, 1f);
                        }
                    }
                }
            }
        });
        for (String n : NAMES) {
            try (AssetFileDescriptor fd = app.getAssets().openFd("waves/" + n + ".wav")) {
                ids.put(n, pool.load(fd, 1));
            } catch (Exception ignored) {
            }
        }
    }

    /** Plays an NVDA sound by name, like nvwave.playWaveFile("waves\\start.wav"). */
    public static synchronized void play(String name) {
        if (pool == null || app == null) return;
        if (!Prefs.sounds(app) && !"error".equals(name)) return;
        Integer id = ids.get(name);
        if (id == null) return;
        if (Boolean.TRUE.equals(loaded.get(name))) {
            pool.play(id, volume, volume, 1, 0, 1f);
        } else {
            pending.put(name, true);
        }
    }
}
