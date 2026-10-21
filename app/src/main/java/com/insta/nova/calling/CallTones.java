package com.insta.nova.calling;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The two caller-side call tones, synthesised with {@link ToneGenerator}.
 *
 * <p>Prompt J asks for three distinct sounds: the callee's ringtone (already
 * implemented with the system {@code Ringtone} in {@code IncomingCallActivity}
 * and {@code CallNotificationHelper}), the outgoing "calling" tone the caller
 * hears while status is {@code calling}, and the ringback tone the caller hears
 * once the callee acks and status becomes {@code ringing}.
 *
 * <p>Method: <b>synthesised via ToneGenerator</b>. No {@code res/raw} assets were
 * added, because a real "ringback" is a network-supplied tone and Instagram's
 * pre-answer tones are simple synthesised staccato bursts rather than sampled
 * audio. ToneGenerator gives a continuous carrier, so the Instagram-style
 * rhythm is layered on top by scheduling short bursts against silence.
 *
 * <p>Every tone is stopped by {@link #stop()} the instant the call state
 * changes — answer, decline, end or timeout — so no tone can outlive its state.
 */
public class CallTones {

    private static final String TAG = "CallTones";

    /** Carrier frequencies. 425Hz is the ITU ringback tone; 440Hz is the common "calling" beep. */
    private static final int TONE_FREQ = ToneGenerator.TONE_SUP_RINGTONE;
    private static final int RINGBACK_FREQ = ToneGenerator.TONE_SUP_BUSY;

    /** Instagram-style rhythm: burst, gap, burst, long pause. */
    private static final long BURST_MS = 420L;
    private static final long GAP_MS = 220L;
    private static final long CYCLE_MS = 2_400L;

    private static CallTones instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator generator;
    private Type current = Type.NONE;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (current == Type.NONE || generator == null) return;
            try {
                if (current == Type.CALLING) {
                    generator.startTone(TONE_FREQ, (int) BURST_MS);
                    handler.postDelayed(loop, CYCLE_MS);
                } else { // RINGBACK
                    generator.startTone(RINGBACK_FREQ, (int) BURST_MS);
                    handler.postDelayed(secondBurst, GAP_MS);
                }
            } catch (Exception e) {
                Log.w(TAG, "tone playback failed: " + e.getMessage());
                release();
            }
        }
    };

    private final Runnable secondBurst = new Runnable() {
        @Override
        public void run() {
            if (current != Type.RINGBACK || generator == null) return;
            try {
                generator.startTone(RINGBACK_FREQ, (int) BURST_MS);
                handler.postDelayed(loop, CYCLE_MS - BURST_MS - GAP_MS - BURST_MS);
            } catch (Exception e) {
                Log.w(TAG, "ringback playback failed: " + e.getMessage());
                release();
            }
        }
    };

    public enum Type {
        /** Caller hears a slow repeating beep while the callee has not acked. */
        CALLING,
        /** Caller hears the ringback rhythm once the callee device has acked. */
        RINGBACK,
        NONE
    }

    public static synchronized CallTones getInstance() {
        if (instance == null) instance = new CallTones();
        return instance;
    }

    private CallTones() {
    }

    /**
     * Starts (or switches to) a caller-side tone.
     *
     * <p>Calling {@link #start} again with the same type is a no-op, so a
     * duplicate Realtime update cannot restart the cycle and produce an
     * audible stutter.
     */
    public synchronized void start(@NonNull Type type) {
        if (type == Type.NONE) {
            stop();
            return;
        }
        if (current == type && generator != null) return;

        stop();
        current = type;
        generator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);
        Log.i(TAG, "START " + type);
        handler.post(loop);
    }

    /**
     * Stops any caller-side tone immediately.
     *
     * <p>Synchronised with {@link #start} so a state change racing a Realtime
     * delivery cannot leave a burst queued behind the new state.
     */
    public synchronized void stop() {
        Type previous = current;
        handler.removeCallbacks(loop);
        handler.removeCallbacks(secondBurst);
        current = Type.NONE;
        release();
        if (previous != Type.NONE) Log.i(TAG, "STOP " + previous);
    }

    @NonNull
    public Type current() {
        return current;
    }

    private void release() {
        ToneGenerator g = generator;
        generator = null;
        if (g != null) {
            try {
                g.stopTone();
                g.release();
            } catch (Exception ignored) {
            }
        }
    }

    /** Releases the generator when the process backgrounds. */
    public static void releaseForProcessDeath(@Nullable Object unused) {
        CallTones t = instance;
        if (t != null) t.stop();
    }
}