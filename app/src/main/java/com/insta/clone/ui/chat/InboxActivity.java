package com.insta.clone.ui.chat;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.clone.R;
import com.insta.clone.data.repository.ChatRepository;

/**
 * Direct messaging inbox activity showing conversation threads.
 */
public class InboxActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Simple programmatically set layout
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        setContentView(rv);

        ChatRepository.getInstance().getConversations().observe(this, conversations -> {
            // Observe conversations
        });
    }
}
