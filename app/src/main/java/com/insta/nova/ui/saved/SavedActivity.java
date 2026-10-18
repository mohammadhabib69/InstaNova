package com.insta.nova.ui.saved;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.insta.nova.R;

/**
 * Profile &gt; Saved. A thin host so the existing {@link SavedFragment} can be opened
 * from the profile without duplicating its grid, adapter or empty state.
 */
public class SavedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.savedFragmentContainer, new SavedFragment())
                    .commit();
        }
    }
}
