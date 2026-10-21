package com.insta.nova.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.insta.nova.R;
import com.insta.nova.data.model.HighlightItem;
import com.insta.nova.data.repository.HighlightRepository;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.List;

/** PAGE 18 — highlight viewer: progress bars, tap next/prev, swipe down closes. */
public class HighlightViewerActivity extends AppCompatActivity {

    public static final String EXTRA_HIGHLIGHT_ID = "EXTRA_HIGHLIGHT_ID";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";

    private ViewPager2 pager;
    private LinearLayout progressRow;
    private final List<HighlightItem> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_highlight_viewer);
        pager = findViewById(R.id.pager);
        progressRow = findViewById(R.id.progressRow);
        TextView title = findViewById(R.id.tvHighlightTitle);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));

        String highlightId = getIntent().getStringExtra(EXTRA_HIGHLIGHT_ID);
        if (highlightId == null) { finish(); return; }

        new HighlightRepository(this).getHighlightItems(highlightId).observe(this, res -> {
            if (res == null || res.status != Resource.Status.SUCCESS || res.data == null) {
                finish();
                return;
            }
            items.clear();
            items.addAll(res.data);
            if (items.isEmpty()) { finish(); return; }
            pager.setAdapter(new PageAdapter());
            buildProgress();
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override public void onPageSelected(int position) { updateProgress(position); }
            });
            updateProgress(0);
        });

        findViewById(R.id.highlightRoot).setOnTouchListener(new SwipeDownCloser());
    }

    private void buildProgress() {
        progressRow.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < items.size(); i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (2 * d), 1f);
            lp.setMarginEnd((int) (3 * d));
            bar.setLayoutParams(lp);
            bar.setBackgroundColor(0x66FFFFFF);
            progressRow.addView(bar);
        }
    }

    private void updateProgress(int position) {
        for (int i = 0; i < progressRow.getChildCount(); i++) {
            progressRow.getChildAt(i).setBackgroundColor(
                    i <= position ? 0xFFFFFFFF : 0x66FFFFFF);
        }
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new Holder(iv);
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            HighlightItem item = items.get(position);
            GlideUtils.load(h.image, "highlights", item.getMediaUrl());
            h.image.setOnClickListener(v -> {
                int next = pager.getCurrentItem() + 1;
                if (next < items.size()) pager.setCurrentItem(next);
                else finish();
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            Holder(ImageView v) { super(v); image = v; }
        }
    }

    /** Vertical drag closes the viewer. */
    private class SwipeDownCloser implements View.OnTouchListener {
        private float startY;
        @Override public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    return false;
                case MotionEvent.ACTION_UP:
                    if (event.getY() - startY > 120 * getResources().getDisplayMetrics().density) {
                        finish();
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }
    }
}
