// Nvidiator Android: speech output. Replaces NVDA's synth drivers (SAPI 5, OneCore, eSpeak)
// with Android's TextToSpeech API, so it speaks with whichever engine the phone uses.
// Speech modes (talk, beeps, off) and the symbol level work as in NVDA.
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public final class Speech {
    public interface UtteranceCallback { void onStart(String id); void onDone(String id); }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile boolean failed = false;
    private final List<String[]> pending = new ArrayList<>();
    private UtteranceCallback callback;
    private int counter = 0;
    private Runnable onReady;

    public Speech(Context c) { ctx = c.getApplicationContext(); }

    public boolean ready() { return ready; }
    public boolean failed() { return failed; }

    public void setCallback(UtteranceCallback cb) { callback = cb; }

    public void start(Runnable onReadyCb) {
        onReady = onReadyCb;
        String engine = Prefs.s(ctx, Prefs.ENGINE, "");
        TextToSpeech.OnInitListener l = status -> main.post(() -> {
            if (status == TextToSpeech.SUCCESS) {
                ready = true;
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { if (callback != null) main.post(() -> callback.onStart(id)); }
                    @Override public void onDone(String id) { if (callback != null) main.post(() -> callback.onDone(id)); }
                    @Override public void onError(String id) { if (callback != null) main.post(() -> callback.onDone(id)); }
                });
                applyVoice();
                for (String[] p : pending) speakRaw(p[0], "1".equals(p[1]), null);
                pending.clear();
                if (onReady != null) onReady.run();
            } else {
                failed = true;
                Log.e("Nvidiator", "TTS init failed: " + status);
                if (onReady != null) onReady.run();
            }
        });
        tts = engine.isEmpty() ? new TextToSpeech(ctx, l) : new TextToSpeech(ctx, l, engine);
    }

    public void restart() {
        shutdown();
        ready = false;
        failed = false;
        start(null);
    }

    public void applyVoice() {
        if (!ready) return;
        // NVDA's 0-100 scale: 50 is the engine's normal speed and pitch, each 25 steps doubles or halves the rate.
        float rate = (float) Math.pow(2.0, (Prefs.rate(ctx) - 50) / 25.0);
        float pitch = (float) Math.pow(2.0, (Prefs.pitch(ctx) - 50) / 50.0);
        tts.setSpeechRate(rate);
        tts.setPitch(pitch);
        float v = Prefs.volume(ctx) / 100f;
        Tones.volume = Math.max(0.2f, v);
        Sounds.volume = Math.max(0.2f, v);
    }

    /** Speaks text through NVDA's symbol processing, honouring the speech mode. */
    public void speak(String text, boolean interrupt) { speak(text, interrupt, null, true); }

    public void speak(String text, boolean interrupt, String utteranceId, boolean processSymbols) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        SpeechHistory.add(text);
        String mode = Prefs.speechMode(ctx);
        if ("off".equals(mode)) return;
        if ("beeps".equals(mode)) {
            // NVDA's beeps speech mode: beepSpeechModePitch (10000 Hz) for 15 ms instead of speech.
            Tones.beep(10000, 15);
            if (utteranceId != null && callback != null) {
                final String id = utteranceId;
                main.postDelayed(() -> { callback.onStart(id); callback.onDone(id); }, 300);
            }
            return;
        }
        if (processSymbols) {
            String p = NvdaPy.processSymbols(text, Prefs.symbolLevel(ctx));
            if (p != null && !p.trim().isEmpty()) text = p;
        }
        speakRaw(text, interrupt, utteranceId);
    }

    private void speakRaw(String text, boolean interrupt, String utteranceId) {
        if (!ready) {
            if (interrupt) pending.clear();
            pending.add(new String[]{text, interrupt ? "1" : "0"});
            return;
        }
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Prefs.volume(ctx) / 100f);
        int max = TextToSpeech.getMaxSpeechInputLength() - 10;
        boolean first = true;
        while (!text.isEmpty()) {
            String chunk = text.length() > max ? text.substring(0, max) : text;
            text = text.substring(chunk.length());
            String id = (utteranceId != null && text.isEmpty()) ? utteranceId : "u" + (counter++);
            tts.speak(chunk, (first && interrupt) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params, id);
            first = false;
        }
    }

    public void cancel() {
        pending.clear();
        if (ready) tts.stop();
    }

    public boolean isSpeaking() { return ready && tts.isSpeaking(); }

    public List<TextToSpeech.EngineInfo> engines() {
        return tts != null ? tts.getEngines() : new ArrayList<>();
    }

    public void shutdown() {
        ready = false;
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
    }
}
