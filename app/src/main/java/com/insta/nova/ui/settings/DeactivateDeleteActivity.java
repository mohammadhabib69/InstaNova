package com.insta.nova.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.remote.ProfileColumns;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.repository.ChatRepository;
import com.insta.nova.ui.auth.AuthActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Deactivate and delete.
 *
 * <p>Deactivate hides the profile (profiles.is_deactivated) and ends the session.
 * Delete requires typing DELETE, re-authenticates with the current password, then
 * removes the Supabase profile row (owned rows cascade) and the Firebase user.
 */
public class DeactivateDeleteActivity extends SettingsPageActivity {

    private RestApi restApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_deactivate_delete);
        restApi = RetrofitClient.getInstance(this).getRestApi();

        addParagraph("Deactivating hides your profile, posts and stories until you log in again. Deleting removes your account permanently.");
        addSection(R.string.st_deactivate);
        addParagraph(R.string.st_deactivate_body);
        addButton(R.string.st_deactivate_confirm, R.color.color_destructive, v -> confirmDeactivate());

        addDivider();
        addSection(R.string.st_delete);
        addParagraph(R.string.st_delete_body);
        addButton(R.string.st_delete, R.color.color_destructive, v -> confirmDelete());
    }

    private void confirmDeactivate() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.st_deactivate_title)
                .setMessage(R.string.st_deactivate_body)
                .setNegativeButton(R.string.st_cancel, null)
                .setPositiveButton(R.string.st_deactivate_confirm, (d, w) -> deactivate())
                .show();
    }

    private void deactivate() {
        String id = SessionManager.getInstance(this).getUserId();
        if (id == null || id.isEmpty()) {
            Toast.makeText(this, R.string.st_something_went_wrong, Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("is_deactivated", true);
        showLoading(true);
        restApi.updateProfile("eq." + id, ProfileColumns.WRITE_ECHO, body)
                .enqueue(new Callback<List<ProfileDto>>() {
                    @Override
                    public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                        showLoading(false);
                        if (!response.isSuccessful()) {
                            Toast.makeText(DeactivateDeleteActivity.this,
                                    "Could not deactivate (" + response.code() + ")", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(DeactivateDeleteActivity.this,
                                R.string.st_deactivated, Toast.LENGTH_LONG).show();
                        signOut();
                    }

                    @Override
                    public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                        showLoading(false);
                        Toast.makeText(DeactivateDeleteActivity.this,
                                t.getMessage() == null ? "Network error" : t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmDelete() {
        int pad = dp(20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, dp(8), pad, 0);
        EditText input = new EditText(this);
        input.setHint(R.string.st_delete_type_hint);
        input.setSingleLine(true);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.st_delete_title)
                .setMessage(R.string.st_delete_body)
                .setView(container)
                .setNegativeButton(R.string.st_cancel, null)
                .setPositiveButton(R.string.st_delete_confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (!"DELETE".equals(input.getText().toString().trim())) {
                        input.setError(getString(R.string.st_delete_wrong));
                        return;
                    }
                    dialog.dismiss();
                    askPasswordThenDelete();
                }));
        dialog.show();
    }

    private void askPasswordThenDelete() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            deleteAccountData(null, null);
            return;
        }
        int pad = dp(20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, dp(8), pad, 0);
        EditText password = new EditText(this);
        password.setHint(R.string.st_current_password);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        container.addView(password, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.st_delete_title)
                .setMessage("Confirm your password to delete the account.")
                .setView(container)
                .setNegativeButton(R.string.st_cancel, null)
                .setPositiveButton(R.string.st_delete_confirm, (d, w) -> {
                    String value = password.getText().toString();
                    if (value.isEmpty()) {
                        Toast.makeText(this, R.string.st_current_password_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showLoading(true);
                    AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), value);
                    user.reauthenticate(credential).addOnCompleteListener(task -> {
                        showLoading(false);
                        if (!task.isSuccessful()) {
                            Toast.makeText(this, R.string.st_reauth_failed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        deleteAccountData(user, null);
                    });
                })
                .show();
    }

    private void deleteAccountData(FirebaseUser user, String unused) {
        showLoading(true);
        restApi.deleteMyAccount(new HashMap<>()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                showLoading(false);
                if (!response.isSuccessful()) {
                    Toast.makeText(DeactivateDeleteActivity.this,
                            getString(R.string.st_delete_failed) + " (" + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                finishDeletion(user);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showLoading(false);
                Toast.makeText(DeactivateDeleteActivity.this, R.string.st_delete_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Firebase user removal is best-effort after the profile is gone. */
    private void finishDeletion(FirebaseUser user) {
        if (user == null) {
            signOut();
            return;
        }
        user.delete().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this,
                        "Profile deleted. Sign in again to finish removing the login.",
                        Toast.LENGTH_LONG).show();
            }
            signOut();
        });
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        SessionManager.getInstance(this).clearSession();
        ChatRepository.getInstance(getApplicationContext()).resetForAccountSwitch();
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
