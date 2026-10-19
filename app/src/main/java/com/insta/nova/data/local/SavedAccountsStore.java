package com.insta.nova.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.insta.nova.data.model.SavedAccount;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers the accounts that have signed in on this device.
 *
 * <p>Backed by EncryptedSharedPreferences. The Firebase SDK keeps only one active
 * session, so a saved account stores identity only — no credential and no refresh
 * token. Picking one on Screen A therefore prefills Screen B and asks for the
 * password again.
 */
public class SavedAccountsStore {

    private static final String FILE_NAME = "insta_saved_accounts";
    private static final String KEY_ACCOUNTS = "accounts";

    private static SavedAccountsStore instance;

    private final SharedPreferences prefs;

    public static synchronized SavedAccountsStore getInstance(Context context) {
        if (instance == null) instance = new SavedAccountsStore(context.getApplicationContext());
        return instance;
    }

    private SavedAccountsStore(Context context) {
        SharedPreferences store;
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            store = EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Keystore can be unavailable on a freshly wiped device; fall back to private prefs.
            store = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = store;
    }

    public List<SavedAccount> list() {
        List<SavedAccount> out = new ArrayList<>();
        String raw = prefs.getString(KEY_ACCOUNTS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) out.add(SavedAccount.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public boolean isEmpty() {
        return list().isEmpty();
    }

    /** Inserts or replaces by userId, newest first. */
    public void upsert(SavedAccount account) {
        if (account == null || account.userId.isEmpty()) return;
        List<SavedAccount> accounts = list();
        List<SavedAccount> next = new ArrayList<>();
        next.add(account);
        for (SavedAccount existing : accounts) {
            if (!existing.userId.equals(account.userId)) next.add(existing);
        }
        persist(next);
    }

    public void remove(String userId) {
        List<SavedAccount> next = new ArrayList<>();
        for (SavedAccount account : list()) {
            if (!account.userId.equals(userId)) next.add(account);
        }
        persist(next);
    }

    public void clear() {
        prefs.edit().remove(KEY_ACCOUNTS).apply();
    }

    private void persist(List<SavedAccount> accounts) {
        JSONArray array = new JSONArray();
        for (SavedAccount account : accounts) {
            try {
                array.put(account.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply();
    }
}
