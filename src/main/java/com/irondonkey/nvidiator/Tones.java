// Nvidiator Android: NVDA's beeps.
// generateBeep is a Java port of NVDA's nvdaHelper/local/beeps.cpp
// (Copyright 2006-2015 NVDA contributers, GNU GPL version 2), so the beeps sound like NVDA's:
// a sine clipped at twice its amplitude, 44.1 kHz stereo, rounded up to whole cycles.
// Not affiliated with or endorsed by NV Access.
package com.irondonkey.nvidiator;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;

public final class Tones {
    private static final int SAMPLE_RATE = 44100;
    private static final int AMPLITUDE = 14000;
    private static HandlerThread thread;
    private static Handler handler;
    private static AudioTrack current;
    public static volatile float volume = 1f;

    private Tones() {}

    /** Port of NVDA's generateBeep: interleaved stereo 16-bit samples. */
    static short[] generateBeep(float hz, int length, int left, int right) {
        int samplesPerCycle = (int) (SAMPLE_RATE / hz);
        if (samplesPerCycle < 1) samplesPerCycle = 1;
        int totalSamples = (int) ((length / 1000.0) / (1.0 / SAMPLE_RATE));
        totalSamples += samplesPerCycle - (totalSamples % samplesPerCycle);
        short[] buf = new short[totalSamples * 2];
        double lpan = (left / 100.0) * AMPLITUDE, rpan = (right / 100.0) * AMPLITUDE;
        double sinFreq = (2.0 * Math.PI) / (SAMPLE_RATE / (double) hz);
        for (int n = 0; n < totalSamples; ++n) {
            double sample = Math.min(Math.max(Math.sin((n % SAMPLE_RATE) * sinFreq) * 2.0, -1.0), 1.0);
            buf[n * 2] = (short) (sample * lpan);
            buf[n * 2 + 1] = (short) (sample * rpan);
        }
        return buf;
    }

    private static synchronized Handler handler() {
        if (handler == null) {
            thread = new HandlerThread("nvda-tones");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        return handler;
    }

    /** Like NVDA's tones.beep(hz, length, left, right). A new beep cuts off the previous one. */
    public static void beep(final float hz, final int length, final int left, final int right) {
        handler().post(() -> {
            try {
                short[] buf = generateBeep(hz, length, left, right);
                if (current != null) {
                    try { current.stop(); } catch (Exception ignored) {}
                    current.release();
                    current = null;
                }
                AudioTrack t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build())
                        .setBufferSizeInBytes(buf.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                t.write(buf, 0, buf.length);
                t.setVolume(volume);
                t.play();
                current = t;
                final AudioTrack done = t;
                handler.postDelayed(() -> {
                    if (current == done) {
                        try { done.stop(); } catch (Exception ignored) {}
                        done.release();
                        current = null;
                    }
                }, length + 200L);
            } catch (Exception ignored) {
            }
        });
    }

    public static void beep(float hz, int length) { beep(hz, length, 50, 50); }

    /** NVDA's progress bar beep: beepMinHZ (110) * 2 ** (percentage / 25), 40 ms. */
    public static void progress(double percentage) {
        beep((float) (110 * Math.pow(2, percentage / 25.0)), 40);
    }
}
