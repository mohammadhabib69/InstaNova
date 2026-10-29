package com.insta.nova.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.insta.nova.R;

/** Contact support: the real support address and a mailto intent. */
public class ContactSupportActivity extends SettingsPageActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(R.string.st_contact_title);
        addParagraph(R.string.st_contact_body);
        addValueRow(getString(R.string.st_email), getString(R.string.st_contact_email));
        addPrimaryButton(R.string.st_contact_open, v -> openMail());
    }

    private void openMail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + getString(R.string.st_contact_email)));
        intent.putExtra(Intent.EXTRA_SUBJECT, "InstaNova support");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.st_no_mail_app, Toast.LENGTH_LONG).show();
        }
    }
}
