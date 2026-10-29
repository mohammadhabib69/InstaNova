package com.insta.nova.ui.story2;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.insta.nova.R;
import com.insta.nova.util.GlideUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gallery picker for the story flow: albums, newest-first grid, badges, multi-select.
 *
 * <p>Single tap opens the editor with one segment; multi-select collects up to ten and
 * opens the editor with all of them, which publish as consecutive stories.
 */
public class StoryGalleryActivity extends AppCompatActivity {

    private static final String TAG = "StoryPicker";
    private static final int MAX_SELECTION = 10;

    private RecyclerView grid;
    private final List<Item> items = new ArrayList<>();
    private final LinkedHashMap<String, String> albums = new LinkedHashMap<>();
    private final List<String> selected = new ArrayList<>();
    private String bucketId = null;
    private boolean multi;
    private TextView albumLabel;
    private TextView multiToggle;
    private TextView nextButton;
    private MediaAdapter adapter;

    static final class Item {
        final Uri uri;
        final boolean video;
        final boolean gif;
        final long durationMs;

        Item(Uri uri, boolean video, boolean gif, long durationMs) {
            this.uri = uri;
            this.video = video;
            this.gif = gif;
            this.durationMs = durationMs;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadAlbums();
        loadMedia(null);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(36), dp(16), dp(10));

        TextView close = text("✕", 22, 0xFFFFFFFF);
        close.setContentDescription(getString(R.string.story_picker_close));
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        albumLabel = text(getString(R.string.story_picker_recents) + " ▾", 15, 0xFFFFFFFF);
        albumLabel.setPadding(dp(8), dp(8), dp(8), dp(8));
        albumLabel.setOnClickListener(v -> showAlbumMenu());
        header.addView(albumLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        multiToggle = text(getString(R.string.story_picker_select), 13, 0xFFFFFFFF);
        multiToggle.setPadding(dp(10), dp(8), dp(10), dp(8));
        multiToggle.setOnClickListener(v -> toggleMulti());
        header.addView(multiToggle);

        TextView camera = text(getString(R.string.story_picker_camera), 13, 0xFFFFFFFF);
        camera.setPadding(dp(10), dp(8), dp(4), dp(8));
        camera.setOnClickListener(v -> {
            finish();
            startActivity(new android.content.Intent(this, StoryCameraActivity.class));
        });
        header.addView(camera);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new MediaAdapter();
        grid.setAdapter(adapter);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(grid, gridLp);

        nextButton = text(getString(R.string.story_picker_next), 15, 0xFF1E88FF);
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setPadding(0, dp(16), 0, dp(16));
        nextButton.setVisibility(View.GONE);
        nextButton.setOnClickListener(v -> openEditor());
        root.addView(nextButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void toggleMulti() {
        multi = !multi;
        selected.clear();
        multiToggle.setText(multi ? R.string.story_picker_select_on : R.string.story_picker_select);
        multiToggle.setTextColor(multi ? 0xFF1E88FF : 0xFFFFFFFF);
        nextButton.setVisibility(multi && !selected.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void showAlbumMenu() {
        PopupMenu menu = new PopupMenu(this, albumLabel);
        menu.getMenu().add(0, 0, 0, getString(R.string.story_picker_recents));
        int id = 1;
        for (Map.Entry<String, String> e : albums.entrySet()) {
            menu.getMenu().add(0, id++, id, e.getValue());
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                bucketId = null;
                albumLabel.setText(getString(R.string.story_picker_recents) + " ▾");
            } else {
                int index = item.getItemId() - 1;
                List<Map.Entry<String, String>> list = new ArrayList<>(albums.entrySet());
                if (index >= 0 && index < list.size()) {
                    bucketId = list.get(index).getKey();
                    albumLabel.setText(list.get(index).getValue() + " ▾");
                }
            }
            loadMedia(bucketId);
            return true;
        });
        menu.show();
    }

    /* ------------------------------------------------------------------ */
    /* MediaStore                                                          */
    /* ------------------------------------------------------------------ */

    private void loadAlbums() {
        albums.clear();
        String[] projection = {
                MediaStore.Files.FileColumns.BUCKET_ID,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME};
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN ("
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + ","
                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        try (Cursor c = getContentResolver().query(
                MediaStore.Files.getContentUri("external"), projection, selection, null,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
            if (c != null) {
                while (c.moveToNext() && albums.size() < 20) {
                    String id = c.getString(0);
                    String name = c.getString(1);
                    if (id != null && name != null && !albums.containsKey(id)) {
                        albums.put(id, name);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "album query failed: " + e.getMessage());
        }
    }

    private void loadMedia(@Nullable String bucket) {
        items.clear();
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DURATION};

        StringBuilder selection = new StringBuilder(MediaStore.Files.FileColumns.MEDIA_TYPE)
                .append(" IN (").append(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
                .append(",").append(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO).append(")");
        List<String> args = new ArrayList<>();
        if (bucket != null) {
            selection.append(" AND ").append(MediaStore.Files.FileColumns.BUCKET_ID).append("=?");
            args.add(bucket);
        }

        try (Cursor c = getContentResolver().query(
                MediaStore.Files.getContentUri("external"), projection,
                selection.toString(), args.toArray(new String[0]),
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
            if (c != null) {
                while (c.moveToNext() && items.size() < 400) {
                    long id = c.getLong(0);
                    int type = c.getInt(1);
                    String mime = c.getString(2);
                    long duration = c.getLong(3);
                    boolean video = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                    boolean gif = mime != null && mime.equalsIgnoreCase("image/gif");
                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri("external"), id);
                    items.add(new Item(uri, video, gif, duration));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "media query failed: " + e.getMessage());
        }
        adapter.notifyDataSetChanged();
    }

    private void openEditor() {
        if (selected.isEmpty()) return;
        StoryEditorActivity.start(this, new ArrayList<>(selected));
        finish();
    }

    /* ------------------------------------------------------------------ */
    /* Adapter                                                             */
    /* ------------------------------------------------------------------ */

    private final class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final FrameLayout cell;
            final ImageView image;
            final TextView badge;
            final TextView check;

            Holder(FrameLayout frame) {
                super(frame);
                cell = frame;
                image = (ImageView) frame.getChildAt(0);
                badge = (TextView) frame.getChildAt(1);
                check = (TextView) frame.getChildAt(2);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(StoryGalleryActivity.this);
            int size = parent.getWidth() > 0 ? parent.getWidth() / 3 : dp(130);
            frame.setLayoutParams(new RecyclerView.LayoutParams(size, size));

            ImageView image = new ImageView(StoryGalleryActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView badge = text("", 11, 0xFFFFFFFF);
            badge.setBackgroundColor(0x66000000);
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.gravity = Gravity.BOTTOM | Gravity.END;
            badgeLp.setMargins(0, 0, dp(6), dp(6));
            frame.addView(badge, badgeLp);

            TextView check = text("", 12, 0xFFFFFFFF);
            check.setGravity(Gravity.CENTER);
            check.setBackgroundColor(0xFF1E88FF);
            FrameLayout.LayoutParams checkLp = new FrameLayout.LayoutParams(dp(22), dp(22));
            checkLp.gravity = Gravity.TOP | Gravity.END;
            checkLp.setMargins(0, dp(6), dp(6), 0);
            frame.addView(check, checkLp);

            return new Holder(frame);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Item item = items.get(position);
            GlideUtils.load(holder.image, "posts", item.uri.toString());

            if (item.video) {
                holder.badge.setVisibility(View.VISIBLE);
                holder.badge.setText(formatDuration(item.durationMs));
            } else if (item.gif) {
                holder.badge.setVisibility(View.VISIBLE);
                holder.badge.setText("GIF");
            } else {
                holder.badge.setVisibility(View.GONE);
            }

            int index = selected.indexOf(item.uri.toString());
            boolean showCheck = multi && index >= 0;
            holder.check.setVisibility(showCheck ? View.VISIBLE : View.GONE);
            holder.check.setText(showCheck ? String.valueOf(index + 1) : "");

            holder.cell.setOnClickListener(v -> onItemTap(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private void onItemTap(Item item) {
        if (!multi) {
            StoryEditorActivity.start(this, java.util.Collections.singletonList(item.uri.toString()));
            finish();
            return;
        }
        String key = item.uri.toString();
        if (selected.contains(key)) {
            selected.remove(key);
        } else {
            if (selected.size() >= MAX_SELECTION) {
                Toast.makeText(this, R.string.story_picker_max, Toast.LENGTH_SHORT).show();
                return;
            }
            selected.add(key);
        }
        nextButton.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private String formatDuration(long ms) {
        long total = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60);
    }

    private TextView text(String value, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
