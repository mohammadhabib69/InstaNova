package com.insta.nova.data.reel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-reel editor state: the timed text lines and the Original/Music volume levels.
 *
 * <p>{@code posts} on Supabase has no jsonb column and {@code create_instagram_post} takes no
 * argument for this, so the record rides in the Firestore {@code posts/{postId}} document the
 * app already keeps for a post. It is written once on publish and read when the reel is played;
 * the clip itself stays untouched, so nothing is re-encoded.
 */
public final class ReelMetaStore {

    public static final String FIELD = "editorMeta";

    /** Last write failure, surfaced to the caller for diagnostics. */
    public static volatile String lastError = "";

    public interface Callback {
        void onLoaded(@NonNull Map<String, Object> meta);
    }

    private ReelMetaStore() {
    }

    /** Saves the meta for a reel. Safe to call before the Firestore doc exists. */
    public static void save(@Nullable String postId, @Nullable String userId,
                            @NonNull Map<String, Object> meta) {
        lastError = "";
        if (postId == null || postId.isEmpty()) {
            lastError = "no-post-id";
            return;
        }
        try {
            // userId is written alongside so the collection rule can authorise the create;
            // the document may not exist yet, because posts live in Supabase.
            // Firestore rules compare request.auth.uid against the owner field, and auth here
            // is Firebase: the Supabase profile id would never match, so the Firebase uid is
            // what the document carries.
            String owner = userId;
            try {
                com.google.firebase.auth.FirebaseUser current =
                        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (current != null) owner = current.getUid();
            } catch (Exception ignored) {
            }
            Map<String, Object> doc = entries(FIELD, meta);
            if (owner != null && !owner.isEmpty()) doc.put("userId", owner);
            doc.put("postId", postId);
            FirebaseFirestore.getInstance().collection("posts").document(postId)
                    .set(doc, SetOptions.merge())
                    .addOnFailureListener(e -> lastError = "write:" + e.getMessage());
        } catch (Exception e) {
            lastError = "exc:" + e.getMessage();
        }
    }

    /** Loads the meta for a reel, handing back an empty map when there is none. */
    public static void load(@Nullable String postId, @NonNull Callback callback) {
        if (postId == null || postId.isEmpty()) {
            callback.onLoaded(new HashMap<>());
            return;
        }
        FirebaseFirestore.getInstance().collection("posts").document(postId).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    Object raw = doc.exists() ? doc.get(FIELD) : null;
                    Map<String, Object> meta = new HashMap<>();
                    if (raw instanceof Map) {
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                            if (entry.getKey() != null) {
                                meta.put(String.valueOf(entry.getKey()), entry.getValue());
                            }
                        }
                    }
                    callback.onLoaded(meta);
                })
                .addOnFailureListener(e -> callback.onLoaded(new HashMap<>()));
    }

    private static Map<String, Object> entries(String key, Map<String, Object> value) {
        Map<String, Object> doc = new HashMap<>();
        doc.put(key, value);
        return doc;
    }

    /** Reads a list-valued key, tolerating both Map rows and absent values. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(@Nullable Map<String, Object> meta, String key) {
        if (meta == null) return new java.util.ArrayList<>();
        Object raw = meta.get(key);
        if (!(raw instanceof List)) return new java.util.ArrayList<>();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object row : (List<Object>) raw) {
            if (row instanceof Map) out.add((Map<String, Object>) row);
        }
        return out;
    }

    public static float number(@Nullable Map<String, Object> meta, String key, float fallback) {
        if (meta == null) return fallback;
        Object raw = meta.get(key);
        if (raw instanceof Number) return ((Number) raw).floatValue();
        return fallback;
    }
}
