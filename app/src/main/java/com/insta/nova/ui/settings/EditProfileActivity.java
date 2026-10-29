package com.insta.nova.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Profile;
import com.insta.nova.data.remote.ProfileColumns;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.api.StorageApi;
import com.insta.nova.data.remote.dto.ProfileDto;
import com.insta.nova.data.repository.ProfileRepository;
import com.insta.nova.data.repository.ProfileWriteCheck;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.ImageUtils;
import com.insta.nova.util.Resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Edit profile with every field wired to the real profiles row. */
public class EditProfileActivity extends SettingsPageActivity {

    private CircleImageView avatar;
    private EditText nameField;
    private EditText usernameField;
    private EditText websiteField;
    private EditText bioField;

    private ProfileRepository profileRepository;
    private RestApi restApi;
    private StorageApi storageApi;
    private String userId;
    private String loadedUsername;
    private String pendingAvatarUrl;
    private boolean populated;

    private ActivityResultLauncher<PickVisualMediaRequest> picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_edit_profile);

        userId = SessionManager.getInstance(this).getUserId();
        profileRepository = new ProfileRepository(this);
        RetrofitClient client = RetrofitClient.getInstance(this);
        restApi = client.getRestApi();
        storageApi = client.getStorageApi();

        picker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                this::onAvatarPicked);

        buildForm();
        loadProfile();
    }

    private void buildForm() {
        avatar = new CircleImageView(this);
        int size = dp(88);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(size, size);
        avatarLp.gravity = Gravity.CENTER_HORIZONTAL;
        avatarLp.topMargin = dp(16);
        avatar.setLayoutParams(avatarLp);
        avatar.setImageResource(R.drawable.ic_person_placeholder);
        avatar.setOnClickListener(v -> openPicker());
        content.addView(avatar);

        TextView change = new TextView(this);
        change.setText(R.string.st_change_profile_picture);
        change.setTextColor(getColor(R.color.color_primary));
        change.setTextSize(14);
        change.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams changeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        change.setLayoutParams(changeLp);
        change.setOnClickListener(v -> openPicker());
        content.addView(change);

        addLabel(R.string.st_name);
        nameField = addInput(R.string.st_name, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        addLabel(R.string.st_username);
        usernameField = addInput(R.string.st_username, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        addLabel(R.string.st_website);
        websiteField = addInput(R.string.st_website, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);

        addLabel(R.string.st_bio);
        bioField = addMultilineInput(R.string.st_bio, 4);

        addPrimaryButton(R.string.st_save, v -> save());
    }

    private void addLabel(int textRes) {
        TextView label = new TextView(this);
        label.setText(textRes);
        label.setTextColor(resolveThemeColor(R.attr.themeTextSecondary, R.color.color_text_secondary));
        label.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(18), dp(10), dp(18), 0);
        label.setLayoutParams(lp);
        content.addView(label);
    }

    private void openPicker() {
        picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void loadProfile() {
        if (userId == null || userId.isEmpty()) return;
        profileRepository.getProfile(userId).observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS
                    || resource.data == null || populated) return;
            Profile profile = resource.data;
            populated = true;
            loadedUsername = profile.getUsername();
            nameField.setText(profile.getFullName());
            usernameField.setText(profile.getUsername());
            websiteField.setText(profile.getWebsite());
            bioField.setText(profile.getBio());
            pendingAvatarUrl = profile.getAvatarUrl();
            GlideUtils.loadAvatar(avatar, profile.getAvatarUrl());
        });
    }

    private void onAvatarPicked(Uri uri) {
        if (uri == null || userId == null || userId.isEmpty()) return;
        showLoading(true);
        byte[] bytes;
        try {
            bytes = ImageUtils.processUriToJpegBytes(this, uri);
        } catch (IOException e) {
            showLoading(false);
            Toast.makeText(this, "Could not read that photo", Toast.LENGTH_LONG).show();
            return;
        }
        String path = userId + "/" + UUID.randomUUID() + ".jpg";
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), bytes);
        storageApi.uploadFile("avatars", path, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                showLoading(false);
                if (!response.isSuccessful()) {
                    Toast.makeText(EditProfileActivity.this,
                            "Upload failed (" + response.code() + ")", Toast.LENGTH_LONG).show();
                    return;
                }
                String base = BuildConfig.SUPABASE_URL.trim();
                if (!base.endsWith("/")) base += "/";
                pendingAvatarUrl = base + "storage/v1/object/public/avatars/" + path;
                GlideUtils.loadAvatar(avatar, pendingAvatarUrl);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showLoading(false);
                Toast.makeText(EditProfileActivity.this,
                        t.getMessage() == null ? "Upload failed" : t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void save() {
        String name = nameField.getText().toString().trim();
        final String username = usernameField.getText().toString().trim().toLowerCase();
        String website = websiteField.getText().toString().trim();
        String bio = bioField.getText().toString().trim();

        if (username.length() < 3 || username.length() > 30 || !username.matches("[a-z0-9._]+")) {
            usernameField.setError("Use 3–30 letters, numbers, dots or underscores");
            return;
        }
        if (bio.length() > 150) {
            bioField.setError("Bio can be up to 150 characters");
            return;
        }
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, R.string.st_something_went_wrong, Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        boolean usernameChanged = !username.equals(loadedUsername);
        if (!usernameChanged) {
            patch(name, username, website, bio);
            return;
        }
        profileRepository.checkUsernameAvailability(username).observe(this, resource -> {
            // The first emission is LOADING; reporting it as a network error flashed a failure
            // toast before the check had run.
            if (resource != null && resource.status == Resource.Status.LOADING) return;
            if (resource == null || resource.status != Resource.Status.SUCCESS) {
                showLoading(false);
                Toast.makeText(this, R.string.st_network_error, Toast.LENGTH_LONG).show();
                return;
            }
            if (resource.data == null || !resource.data) {
                showLoading(false);
                usernameField.setError("That username is already taken");
                return;
            }
            patch(name, username, website, bio);
        });
    }

    private void patch(String name, String username, String website, String bio) {
        Map<String, Object> body = new HashMap<>();
        body.put("full_name", name);
        body.put("username", username);
        body.put("website", website);
        body.put("bio", bio);
        if (pendingAvatarUrl != null) body.put("avatar_url", pendingAvatarUrl);

        restApi.updateProfile("eq." + userId, ProfileColumns.WRITE_ECHO, body)
                .enqueue(new Callback<List<ProfileDto>>() {
                    @Override
                    public void onResponse(Call<List<ProfileDto>> call, Response<List<ProfileDto>> response) {
                        showLoading(false);
                        if (!response.isSuccessful()
                                || !ProfileWriteCheck.confirmed(response.body(), userId)) {
                            Toast.makeText(EditProfileActivity.this,
                                    ProfileWriteCheck.ZERO_ROWS_MESSAGE, Toast.LENGTH_LONG).show();
                            return;
                        }
                        SessionManager session = SessionManager.getInstance(EditProfileActivity.this);
                        session.updateUsername(username);
                        if (pendingAvatarUrl != null) session.updateAvatar(pendingAvatarUrl);
                        Toast.makeText(EditProfileActivity.this,
                                "Profile updated", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(Call<List<ProfileDto>> call, Throwable t) {
                        showLoading(false);
                        Toast.makeText(EditProfileActivity.this,
                                t.getMessage() == null ? "Network error" : t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
