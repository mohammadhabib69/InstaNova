package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.nova.R;
import com.insta.nova.data.repository.SettingsRepository;
import com.insta.nova.databinding.ActivityAccountCenterBinding;
import com.insta.nova.databinding.ItemSettingsHeaderBinding;
import com.insta.nova.databinding.ItemSettingsRowBinding;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Accounts Center — the same list style as PAGE 13, containing the two rows the
 * reference shows: password/security (real password change) and personal details
 * (read-only email, username and joined date straight from profiles).
 */
public class AccountCenterActivity extends AppCompatActivity {

    private static final class Row {
        final boolean header;
        final int labelRes;
        final int iconRes;
        String value;
        Row(int labelRes, int iconRes) {
            this.header = false;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }
        Row(int labelRes) {
            this.header = true;
            this.labelRes = labelRes;
            this.iconRes = 0;
        }
    }

    private ActivityAccountCenterBinding binding;
    private SettingsRepository repository;
    private final List<Row> rows = new ArrayList<>();
    private Adapter adapter;

    private final Row passwordRow = new Row(R.string.settings_password_security, R.drawable.ic_lock);
    private final Row emailRow = new Row(R.string.settings_email, 0);
    private final Row usernameRow = new Row(R.string.settings_username, 0);
    private final Row joinedRow = new Row(R.string.settings_joined, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new SettingsRepository(this);
        binding.btnBack.setOnClickListener(v -> finish());

        rows.add(new Row(R.string.settings_section_account));
        rows.add(passwordRow);
        rows.add(emailRow);
        rows.add(usernameRow);
        rows.add(joinedRow);

        binding.rvAccountCenter.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        binding.rvAccountCenter.setAdapter(adapter);

        repository.getMyProfileRow().observe(this, resource -> {
            if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null) return;
            Object email = resource.data.get("email");
            Object username = resource.data.get("username");
            Object created = resource.data.get("created_at");
            emailRow.value = email == null ? "—" : String.valueOf(email);
            usernameRow.value = username == null ? "—" : String.valueOf(username);
            joinedRow.value = created == null ? "—" : shorten(String.valueOf(created));
            adapter.notifyDataSetChanged();
        });
    }

    private static String shorten(String iso) {
        return iso.length() >= 10 ? iso.substring(0, 10) : iso;
    }

    private void showChangePasswordDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);

        final EditText newPassword = new EditText(this);
        newPassword.setHint(R.string.settings_new_password);
        newPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPassword.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        container.addView(newPassword);

        final EditText confirmPassword = new EditText(this);
        confirmPassword.setHint(R.string.settings_confirm_password);
        confirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPassword.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        container.addView(confirmPassword);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_change_password)
                .setView(container)
                .setPositiveButton(R.string.settings_ed_done_ok, (dialog, which) -> {
                    String first = newPassword.getText().toString();
                    String second = confirmPassword.getText().toString();
                    if (first.length() < 6) {
                        Toast.makeText(this, R.string.settings_password_too_short, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!first.equals(second)) {
                        Toast.makeText(this, R.string.settings_password_mismatch, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.updatePassword(first).observe(this, resource -> {
                        if (resource == null) return;
                        if (resource.status == Resource.Status.SUCCESS) {
                            Toast.makeText(this, R.string.settings_password_saved, Toast.LENGTH_SHORT).show();
                        } else if (resource.status == Resource.Status.ERROR) {
                            Toast.makeText(this, resource.message == null
                                    ? getString(R.string.settings_password_mismatch) : resource.message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return rows.get(position).header ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 0) {
                return new HeaderHolder(ItemSettingsHeaderBinding.inflate(inflater, parent, false));
            }
            return new RowHolder(ItemSettingsRowBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).binding.tvSection.setText(row.labelRes);
                return;
            }
            RowHolder rowHolder = (RowHolder) holder;
            ItemSettingsRowBinding b = rowHolder.binding;
            b.tvLabel.setText(row.labelRes);
            b.tvDescription.setVisibility(View.GONE);
            if (row.iconRes != 0) {
                b.ivIcon.setImageResource(row.iconRes);
                b.ivIcon.setVisibility(View.VISIBLE);
            } else {
                b.ivIcon.setVisibility(View.GONE);
            }
            // Personal detail rows are read-only values, not navigable rows.
            if (row.value != null) {
                b.tvValue.setText(row.value);
                b.tvValue.setVisibility(View.VISIBLE);
                b.ivChevron.setVisibility(View.GONE);
                b.layoutSettingsRow.setClickable(false);
            } else {
                b.tvValue.setVisibility(View.GONE);
                b.ivChevron.setVisibility(View.VISIBLE);
                b.layoutSettingsRow.setClickable(true);
                b.layoutSettingsRow.setOnClickListener(v -> showChangePasswordDialog());
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemSettingsHeaderBinding binding;
        HeaderHolder(ItemSettingsHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final ItemSettingsRowBinding binding;
        RowHolder(ItemSettingsRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
