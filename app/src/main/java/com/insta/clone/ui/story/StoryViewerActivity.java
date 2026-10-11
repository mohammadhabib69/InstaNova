package com.insta.clone.ui.story;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Fullscreen activity for viewing stories with tap left/right progression.
 */
public class StoryViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String username = getIntent().getStringExtra("username");
        TextView tv = new TextView(this);
        tv.setText(username != null ? "Stories of @" + username : "Stories");
        tv.setPadding(32, 32, 32, 32);
        setContentView(tv);
    }
}
