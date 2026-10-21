package com.insta.nova.ui.chat;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * PAGE 17 — double-tap to toggle a heart reaction.
 *
 * <p>Attached next to {@code setOnClickListener} / {@code setOnLongClickListener},
 * so it never consumes the event: single taps still open the message and long
 * presses still open the reaction popup.
 */
public class DoubleTapDetector implements View.OnTouchListener {

    private final Runnable onDoubleTap;
    private GestureDetector detector;

    public DoubleTapDetector(Runnable onDoubleTap) {
        this.onDoubleTap = onDoubleTap;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (detector == null) {
            detector = new GestureDetector(v.getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            if (onDoubleTap != null) onDoubleTap.run();
                            return true;
                        }
                    });
        }
        detector.onTouchEvent(event);
        return false;
    }
}
