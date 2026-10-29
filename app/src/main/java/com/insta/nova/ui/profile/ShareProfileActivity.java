package com.insta.nova.ui.profile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.util.Constants;
import com.insta.nova.util.GlideUtils;

/** Profile share page: copy the real profile link or hand it to the share sheet. */
public class ShareProfileActivity extends AppCompatActivity {

    private String link;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_profile);

        SessionManager session = SessionManager.getInstance(this);
        String username = session.getUsername();
        link = Constants.PROFILE_URL_BASE + username;

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        GlideUtils.loadAvatar(findViewById(R.id.ivAvatar), session.getAvatarUrl());
        ((TextView) findViewById(R.id.tvUsername)).setText("@" + username);
        ((TextView) findViewById(R.id.tvName)).setText(session.getUsername());
        ((TextView) findViewById(R.id.tvLink)).setText(link);

        findViewById(R.id.btnCopyLink).setOnClickListener(v -> copyLink());
        findViewById(R.id.btnShare).setOnClickListener(v -> share());
    }

    private void copyLink() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("InstaNova profile", link));
            Toast.makeText(this, R.string.st_link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        startActivity(Intent.createChooser(intent, getString(R.string.st_share)));
    }
}
