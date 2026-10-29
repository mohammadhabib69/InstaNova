package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.insta.nova.R;
import com.insta.nova.data.local.AppSettingsStore;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.repository.SettingsRepository;
import com.insta.nova.util.Resource;

import java.util.Map;

/** Read-only account information assembled from the real profile row and Firebase. */
public class AccountInfoActivity extends SettingsPageActivity {

    private View nameRow;
    private View usernameRow;
    private View typeRow;
    private View joinedRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_account_info_title);

        SessionManager session = SessionManager.getInstance(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        AppSettingsStore settings = new AppSettingsStore(this);

        nameRow = addValueRow(getString(R.string.st_name), session.getUsername());
        usernameRow = addValueRow(getString(R.string.st_username), "@" + session.getUsername());
        addValueRow(getString(R.string.st_email), user == null ? null : user.getEmail());
        addValueRow(getString(R.string.st_phone),
                user == null || user.getPhoneNumber() == null
                        ? getString(R.string.st_not_set) : user.getPhoneNumber());
        typeRow = addValueRow(getString(R.string.st_account_type_label),
                accountTypeLabel(settings.getString("account_type", "personal")));
        joinedRow = addValueRow(getString(R.string.st_date_joined), "…");

        SettingsRepository repository = new SettingsRepository(this);
        repository.getMyProfileRow().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS
                    || resource.data == null) return;
            Map<String, Object> row = resource.data;
            Object fullName = row.get("full_name");
            Object username = row.get("username");
            Object type = row.get("account_type");
            Object joined = row.get("created_at");

            if (fullName != null && !String.valueOf(fullName).trim().isEmpty()) {
                setValue(nameRow, String.valueOf(fullName));
            }
            if (username != null) setValue(usernameRow, "@" + username);
            if (type != null) setValue(typeRow, accountTypeLabel(String.valueOf(type)));
            if (joined != null) {
                String date = String.valueOf(joined);
                setValue(joinedRow, date.length() >= 10 ? date.substring(0, 10) : date);
            }
        });
    }

    private void setValue(View row, String value) {
        if (row == null) return;
        TextView tv = row.findViewById(R.id.tv_value);
        if (tv != null) tv.setText(value);
    }

    private String accountTypeLabel(String type) {
        if ("creator".equals(type)) return getString(R.string.st_creator);
        if ("business".equals(type)) return getString(R.string.st_business);
        return getString(R.string.st_personal);
    }
}
