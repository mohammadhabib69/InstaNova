package com.insta.nova.data.firebase;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.insta.nova.BuildConfig;
import com.insta.nova.data.firebase.model.UserDoc;
import com.insta.nova.data.repository.UserRepository;
import com.insta.nova.util.Resource;

/**
 * Shadow-write hook for task P1-08 ("identity mirror").
 *
 * <p>When {@code BuildConfig.FIREBASE_MIRROR} is true, every established session also writes
 * {@code users/{uid}} + {@code publicProfiles/{uid}} + the {@code usernames} reservation.
 * Supabase stays the source of truth: the mirror never reads back into the UI, never
 * blocks the sign-in path, and a failure is recorded as a non-fatal only.
 *
 * <p>Default is <b>false</b>, so the shipping app is byte-for-byte identical in behaviour.
 * Flip the flag in {@code local.properties} ({@code firebase.mirror=true}) to populate
 * Firestore in a staging build; that is how P11's data migration gets a live rehearsal.
 */
public final class ProfileMirror {

    private ProfileMirror() {
    }

    public static void onSessionEstablished(@Nullable String uid, @Nullable String profileId,
                                            @Nullable String username, @Nullable String fullName,
                                            @Nullable String avatarUrl, @Nullable String birthday) {
        if (!BuildConfig.FIREBASE_MIRROR) return;
        if (uid == null || uid.isEmpty() || username == null || username.isEmpty()) return;

        final UserDoc doc = new UserDoc();
        doc.uid = uid;
        doc.legacyProfileId = profileId;
        doc.username = username;
        doc.fullName = fullName == null ? "" : fullName;
        doc.avatarUrl = avatarUrl;
        doc.birthday = birthday;
        doc.createdAt = System.currentTimeMillis();
        doc.updatedAt = doc.createdAt;

        new Handler(Looper.getMainLooper()).post(() -> {
            final LiveData<Resource<Void>> live = new UserRepository().writeSelfProfileOnly(doc);
            live.observeForever(new Observer<Resource<Void>>() {
                @Override
                public void onChanged(Resource<Void> resource) {
                    if (resource == null || resource.status == Resource.Status.LOADING) return;
                    live.removeObserver(this);
                    if (resource.status == Resource.Status.SUCCESS) {
                        CrashReporter.log("profile mirror ok: " + uid);
                    } else {
                        CrashReporter.record(new IllegalStateException(
                                "profile mirror failed: " + resource.message), "profile_mirror");
                    }
                }
            });
        });
    }

    /** Exposed for the migration harness: builds the document without writing it. */
    @NonNull
    public static UserDoc buildDoc(@NonNull String uid, @Nullable String profileId,
                                   @NonNull String username, @Nullable String fullName,
                                   @Nullable String avatarUrl, @Nullable String birthday) {
        UserDoc doc = new UserDoc();
        doc.uid = uid;
        doc.legacyProfileId = profileId;
        doc.username = username;
        doc.fullName = fullName == null ? "" : fullName;
        doc.avatarUrl = avatarUrl;
        doc.birthday = birthday;
        doc.createdAt = System.currentTimeMillis();
        doc.updatedAt = doc.createdAt;
        return doc;
    }
}
