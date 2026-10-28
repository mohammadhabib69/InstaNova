package com.insta.nova.data.repository;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.insta.nova.data.firebase.CrashReporter;
import com.insta.nova.data.firebase.FirebaseData;
import com.insta.nova.util.Resource;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-device FCM token registry (tasks P7-04, P7-09).
 *
 * <p>Replaces the two places a token was stored before the migration — a column on
 * {@code profiles} and a row in {@code user_devices} — with one document per install:
 * {@code devices/{uid}_{deviceId}}. The device id is the Android ID, which is stable for
 * the install, so re-registering refreshes `lastSeenAt` instead of appending a second
 * token for the same phone.
 *
 * <p>Why the uid prefix: a Firestore rule can only check the document it is looking at, so
 * putting the owner in the id lets "read own devices" stay a plain field check.
 */
public class DeviceRepository {

    private final FirebaseFirestore db;

    public DeviceRepository() {
        this.db = FirebaseData.firestore();
    }

    /** Stable per install; Secure.ANDROID_ID survives app updates. */
    @SuppressWarnings("HardwareIds")
    public static String deviceId(@NonNull Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null || id.isEmpty() ? "unknown" : id;
    }

    public static String documentId(@NonNull String uid, @NonNull String deviceId) {
        return uid + "_" + deviceId;
    }

    /** Called after login and on every FCM token refresh. */
    public LiveData<Resource<Void>> register(@NonNull String uid, @NonNull String deviceId,
                                             @NonNull String token) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        String id = documentId(uid, deviceId);

        Map<String, Object> doc = new HashMap<>();
        doc.put("uid", uid);
        doc.put("deviceId", deviceId);
        doc.put("token", token);
        doc.put("platform", "android");
        doc.put("sdkInt", Build.VERSION.SDK_INT);
        doc.put("model", Build.MODEL == null ? "" : Build.MODEL);
        doc.put("lastSeenAt", FieldValue.serverTimestamp());

        db.collection("devices").document(id).set(doc, SetOptions.merge())
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> {
                    CrashReporter.record(e, "device_register");
                    out.setValue(Resource.error("Couldn't register this device."));
                });
        return out;
    }

    /** Called on logout so this phone stops receiving the previous account's pushes. */
    public LiveData<Resource<Void>> unregister(@NonNull String uid, @NonNull String deviceId) {
        MutableLiveData<Resource<Void>> out = new MutableLiveData<>(Resource.loading());
        db.collection("devices").document(documentId(uid, deviceId)).delete()
                .addOnSuccessListener(v -> out.setValue(Resource.success(null)))
                .addOnFailureListener(e -> {
                    CrashReporter.record(e, "device_unregister");
                    out.setValue(Resource.error("Couldn't unregister this device."));
                });
        return out;
    }
}
