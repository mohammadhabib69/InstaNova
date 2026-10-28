package com.insta.nova.data.firebase;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;

/**
 * Single decision point for time in Firebase (task P2-09).
 *
 * <p>Server timestamps for anything the client writes, so a device with a wrong clock
 * cannot poison ordering. Millis only where the value must be readable before the server
 * acknowledges the write (story expiry checks, optimistic UI).
 */
public final class Timestamps {

    /** Story lifetime, matching the current Supabase `expires_at` behaviour. */
    public static final long STORY_TTL_MILLIS = 24L * 60L * 60L * 1000L;

    private Timestamps() {
    }

    /** Use for every `createdAt` / `updatedAt` written by the client. */
    public static FieldValue serverNow() {
        return FieldValue.serverTimestamp();
    }

    public static Timestamp now() {
        return Timestamp.now();
    }

    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    public static long storyExpiry(@Nullable Long createdAtMillis) {
        return (createdAtMillis == null ? nowMillis() : createdAtMillis) + STORY_TTL_MILLIS;
    }

    /** Null-safe expiry check: a missing timestamp is treated as expired, never as live. */
    public static boolean isExpired(@Nullable Long createdAtMillis, long nowMillis) {
        if (createdAtMillis == null) {
            return true;
        }
        return nowMillis >= storyExpiry(createdAtMillis);
    }
}
