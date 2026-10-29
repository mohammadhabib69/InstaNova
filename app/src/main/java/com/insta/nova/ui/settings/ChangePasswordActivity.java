package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.insta.nova.R;

/**
 * Change password: Firebase password-reset email plus a real in-app change that
 * re-authenticates with the current password first.
 */
public class ChangePasswordActivity extends SettingsPageActivity {

    private EditText current;
    private EditText next;
    private EditText confirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_change_password_title);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user == null || user.getEmail() == null ? "" : user.getEmail();

        addParagraph("We'll email a reset link to " + (email.isEmpty() ? "your account email" : email) + ".");
        addButton(R.string.st_send_reset_email, R.color.color_primary, v -> sendReset(email));
        addDivider();
        addSection(R.string.st_change_password);

        current = addInput(R.string.st_current_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        next = addInput(R.string.st_new_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm = addInput(R.string.st_confirm_new_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addPrimaryButton(R.string.st_update_password, v -> updatePassword());
    }

    private void sendReset(String email) {
        if (email.isEmpty()) {
            Toast.makeText(this, "This account has no email address.", Toast.LENGTH_LONG).show();
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, getString(R.string.st_reset_email_sent, email),
                                Toast.LENGTH_LONG).show();
                    } else {
                        String message = task.getException() == null
                                ? "Could not send the reset email."
                                : task.getException().getMessage();
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updatePassword() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Your session expired. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }
        String currentValue = current.getText().toString();
        String nextValue = next.getText().toString();
        String confirmValue = confirm.getText().toString();

        if (currentValue.isEmpty()) {
            Toast.makeText(this, R.string.st_current_password_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (nextValue.length() < 6) {
            Toast.makeText(this, R.string.st_password_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!nextValue.equals(confirmValue)) {
            Toast.makeText(this, R.string.st_password_mismatch, Toast.LENGTH_SHORT).show();
            return;
        }
        if (user.getEmail() == null) {
            Toast.makeText(this, "This account has no password to change.", Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentValue);
        user.reauthenticate(credential).addOnCompleteListener(authTask -> {
            if (!authTask.isSuccessful()) {
                showLoading(false);
                String message = authTask.getException() == null
                        ? getString(R.string.st_reauth_failed)
                        : authTask.getException().getMessage();
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                return;
            }
            user.updatePassword(nextValue).addOnCompleteListener(updateTask -> {
                showLoading(false);
                if (updateTask.isSuccessful()) {
                    current.setText("");
                    next.setText("");
                    confirm.setText("");
                    Toast.makeText(this, R.string.st_password_updated, Toast.LENGTH_LONG).show();
                } else {
                    String message = updateTask.getException() == null
                            ? "Could not update your password."
                            : updateTask.getException().getMessage();
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
