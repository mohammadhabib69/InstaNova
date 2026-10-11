package com.insta.clone.ui.chat;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.insta.clone.R;
import com.insta.clone.ui.widget.PinchZoomImageView;
import com.insta.clone.util.GlideUtils;

/**
 * Full-screen image viewer with pinch-to-zoom and back/close button.
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "EXTRA_IMAGE_URL";
    public static final String EXTRA_BUCKET = "EXTRA_BUCKET";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String bucket = getIntent().getStringExtra(EXTRA_BUCKET);
        if (bucket == null || bucket.isEmpty()) {
            bucket = "chat";
        }

        PinchZoomImageView ivFullImage = findViewById(R.id.iv_full_image);
        ImageView ivClose = findViewById(R.id.iv_close);

        ivClose.setOnClickListener(v -> finish());

        if (imageUrl != null && !imageUrl.isEmpty()) {
            GlideUtils.load(ivFullImage, bucket, imageUrl);
        }
    }
}
