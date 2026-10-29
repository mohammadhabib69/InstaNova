package com.insta.nova.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.insta.nova.BuildConfig;
import com.insta.nova.R;
import com.insta.nova.data.repository.RelationshipRepository;
import com.insta.nova.util.Resource;

/** Report a problem — inserts into the Supabase `reports` table. */
public class ReportProblemActivity extends SettingsPageActivity {

    private static final String[] CATEGORY_LABELS = {"Something isn't working", "Abuse or spam",
            "Login or account", "Something else"};
    private static final String[] CATEGORY_VALUES = {"bug", "abuse", "login", "other"};

    private EditText message;
    private int categoryIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_report_title);
        render();
    }

    private void render() {
        content.removeAllViews();
        addParagraph("Tell us what went wrong. Your report includes the app version so we can reproduce it.");

        addSection(R.string.st_report_category);
        addRadioGroup(null, CATEGORY_LABELS, categoryIndex, (group, checkedId) -> {
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index >= 0 && index < CATEGORY_VALUES.length) categoryIndex = index;
        });

        addSection(R.string.st_report_message);
        message = addMultilineInput(R.string.st_report_hint, 5);

        addPrimaryButton(R.string.st_report_submit, v -> submit());
    }

    private void submit() {
        String text = message == null ? "" : message.getText().toString().trim();
        if (text.isEmpty()) {
            message.setError(getString(R.string.st_report_required));
            return;
        }
        showLoading(true);
        new RelationshipRepository(this)
                .submitReport(CATEGORY_VALUES[categoryIndex], text, BuildConfig.VERSION_NAME)
                .observe(this, resource -> {
                    if (resource == null) return;
                    if (resource.status == Resource.Status.SUCCESS) {
                        showLoading(false);
                        showThanks();
                        return;
                    }
                    if (resource.status != Resource.Status.ERROR) return;
                    showLoading(false);
                    String msg = resource.message == null
                            ? getString(R.string.st_report_failed)
                            : getString(R.string.st_report_failed) + "\n" + resource.message;
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private void showThanks() {
        content.removeAllViews();
        addSpacer(24);
        addParagraph(getString(R.string.st_report_thanks));
        addParagraph("Our team reviews every report. We won't reply to this report unless we need more information.");
        addPrimaryButton(R.string.st_done, v -> finish());
    }
}
