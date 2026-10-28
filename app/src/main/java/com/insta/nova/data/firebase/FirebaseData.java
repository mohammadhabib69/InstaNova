package com.insta.nova.data.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

/**
 * Single entry point for the Firebase data plane (Firestore + Realtime Database).
 *
 * <p>Phase 2 foundation (task P2-06). Nothing in the shipping UI calls this yet: the
 * existing Supabase repositories remain the active data source until their Firestore
 * replacement is feature-complete and switched over in later phases.
 *
 * <p>Offline policy (ARCHITECTURE §7): Firestore keeps its persistent cache, RTDB keeps
 * its own disk cache. Both are required because the app already ships an offline post
 * queue and a "works on a bad connection" chat UI; dropping local caching would be a
 * user-visible regression.
 */
public final class FirebaseData {

    /**
     * Firestore disk cache. Android's default is 100 MB, which is ample for a feed of a
     * few hundred posts plus profiles; sizing it explicitly (task P2-07) means the number
     * is a decision in code rather than a library default that can shift between releases.
     */
    private static final long FIRESTORE_CACHE_BYTES = 100L * 1024 * 1024;

    /**
     * RTDB disk cache. Android's default is 10 MB, which is too small for a chat list plus
     * recent threads — a cold start would evict messages the user expects to see offline.
     * 50 MB covers the p95 thread count without turning the cache into a storage hog.
     */
    private static final long RTDB_CACHE_BYTES = 50L * 1024 * 1024;

    private static volatile FirebaseFirestore firestore;
    private static volatile FirebaseDatabase database;

    private FirebaseData() {
    }

    /**
     * Firestore with persistence enabled. Safe to call from any thread; the instance is
     * cached. If Firebase failed to initialise this throws, which the caller must treat
     * as "Firebase data plane unavailable" rather than swallowing (audit finding S11).
     */
    @NonNull
    public static FirebaseFirestore firestore() {
        FirebaseFirestore local = firestore;
        if (local != null) return local;
        synchronized (FirebaseData.class) {
            if (firestore == null) {
                FirebaseApp app = FirebaseApp.getInstance();
                FirebaseFirestore db = FirebaseFirestore.getInstance(app);
                // Must be applied before the first query; setFirestoreSettings throws
                // IllegalStateException once the client has been used. This runs inside
                // the double-checked init, so it is always first.
                FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                                .setSizeBytes(FIRESTORE_CACHE_BYTES)
                                .build())
                        .build();
                db.setFirestoreSettings(settings);
                firestore = db;
            }
            return firestore;
        }
    }

    /** Realtime Database root, used by chat/presence/call signalling from Phase 6. */
    @NonNull
    public static FirebaseDatabase database() {
        FirebaseDatabase local = database;
        if (local != null) return local;
        synchronized (FirebaseData.class) {
            if (database == null) {
                FirebaseDatabase db = FirebaseDatabase.getInstance(FirebaseApp.getInstance());
                // Both calls must precede the first read/write on this instance.
                db.setPersistenceEnabled(true);
                db.setPersistenceCacheSizeBytes(RTDB_CACHE_BYTES);
                database = db;
            }
            return database;
        }
    }

    /**
     * Drops the Firestore disk cache for the signed-out account (task P2-07).
     *
     * <p>Firestore serves offline reads from cache <em>without re-evaluating rules</em>. On a
     * shared device that means account B could read account A's cached feed while offline.
     * The cache lives in app-private storage so no other app can reach it, but a second
     * sign-in on the same install can. Call this from sign-out once the new stack is live.
     *
     * <p>Semantics are awkward by design of the SDK: {@code terminate()} must complete
     * before {@code clearPersistence()}, both are async, and the cached instance must be
     * dropped so the next call re-initialises. A failed clear leaves the old cache in
     * place — it does not throw at the caller, so treat it as best-effort and never as a
     * security guarantee.
     *
     * <p>Realtime Database has <strong>no equivalent API</strong>: its disk cache cannot be
     * purged at runtime. See limitation L4 in OFFLINE_POLICY.md.
     */
    public static void purgeLocalCaches() {
        FirebaseFirestore db;
        synchronized (FirebaseData.class) {
            db = firestore;
            firestore = null;
        }
        if (db == null) return;
        db.terminate().continueWithTask(task -> db.clearPersistence());
        FirebaseDatabase rtdb;
        synchronized (FirebaseData.class) {
            rtdb = database;
        }
        // Best effort only — goOffline stops new sync; it does not delete cached rows.
        if (rtdb != null) {
            try {
                rtdb.goOffline();
            } catch (RuntimeException ignored) {
                // Already offline or already used in a way that forbids the transition.
            }
        }
    }

    /** Test/diagnostic hook: true when FirebaseApp has been initialised. */
    public static boolean isAvailable() {
        try {
            FirebaseApp.getInstance();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
