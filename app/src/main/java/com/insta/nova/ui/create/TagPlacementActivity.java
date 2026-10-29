package com.insta.nova.ui.create;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.insta.nova.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Tap-to-place tagging.
 *
 * <p>{@code post_tags.x / y} are normalised 0..1 coordinates over the photo and the
 * table only accepts that range, so this screen turns taps into those numbers: the
 * first tap places the first tagged account, the next tap the next one, in the same
 * order {@link CreatePostActivity} keeps them. A tap near a placed marker takes that
 * marker back off the photo.
 *
 * <p>It is an overlay inside the activity's own content view rather than a Dialog: a
 * dialog window that wraps its content collapses a weighted child to zero height,
 * which left the photo invisible and the composer showing through.
 */
public class TagPlacementActivity extends Activity {

    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_IDS = "ids";
    public static final String EXTRA_NAMES = "names";
    public static final String EXTRA_XS = "xs";
    public static final String EXTRA_YS = "ys";

    /** One placed tag: the account id, a display name and its normalised point. */
    static final class Point {
        final String userId;
        final String name;
        float x;
        float y;

        Point(String userId, String name, float x, float y) {
            this.userId = userId;
            this.name = name;
            this.x = x;
            this.y = y;
        }

        boolean placed() {
            return x >= 0f && y >= 0f;
        }
    }

    interface Callback {
        void onDone(List<Point> points);
    }


    @Override
    protected void onCreate(@Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = this;
        // The picker hands over a content:// uri, and this screen is the only place
        // the tag positions can be set, so the window must be opaque: a translucent
        // window let the previous screen's stale pixels show through the photo area.
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.BLACK));
        }
        Uri media = getIntent().getStringExtra(EXTRA_IMAGE_URI) == null
                ? null : Uri.parse(getIntent().getStringExtra(EXTRA_IMAGE_URI));
        java.util.ArrayList<String> ids = getIntent().getStringArrayListExtra(EXTRA_IDS);
        java.util.ArrayList<String> names = getIntent().getStringArrayListExtra(EXTRA_NAMES);
        float[] xs = getIntent().getFloatArrayExtra(EXTRA_XS);
        float[] ys = getIntent().getFloatArrayExtra(EXTRA_YS);
        final List<Point> points = new ArrayList<>();
        if (ids != null) {
            for (int i = 0; i < ids.size(); i++) {
                String name = names != null && i < names.size() ? names.get(i) : "";
                float x = xs != null && i < xs.length ? xs[i] : -1f;
                float y = ys != null && i < ys.length ? ys[i] : -1f;
                points.add(new Point(ids.get(i), name, x, y));
            }
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(context, 16);
        root.setPadding(pad, dp(context, 48), pad, dp(context, 24));

        TextView title = new TextView(context);
        title.setText(R.string.tag_placement_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(context, 10));
        root.addView(title);

        FrameLayout canvas = new FrameLayout(context);
        canvas.setBackgroundColor(Color.BLACK);
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        root.addView(canvas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(screenHeight * 0.60f)));

        ImageView photo = new ImageView(context);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setBackgroundColor(Color.BLACK);
        canvas.addView(photo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (media != null) {
            Glide.with(context).load(media).fitCenter().into(photo);
        }

        final List<TextView> markers = new ArrayList<>();
        final Runnable repaint = () -> {
            for (TextView m : markers) canvas.removeView(m);
            markers.clear();
            for (Point p : points) {
                if (!p.placed()) continue;
                final TextView chip = new TextView(context);
                chip.setText(p.name);
                chip.setTextColor(Color.WHITE);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                chip.setBackgroundResource(R.drawable.bg_picker_mode_pill);
                chip.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
                canvas.addView(chip);
                chip.post(() -> {
                    chip.setX(p.x * canvas.getWidth() - chip.getWidth() / 2f);
                    chip.setY(p.y * canvas.getHeight() - chip.getHeight() / 2f);
                });
                markers.add(chip);
            }
        };

        canvas.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            int width = Math.max(1, canvas.getWidth());
            int height = Math.max(1, canvas.getHeight());
            float slop = dp(context, 28);
            for (Point p : points) {
                if (!p.placed()) continue;
                if (Math.abs(p.x * width - event.getX()) < slop
                        && Math.abs(p.y * height - event.getY()) < slop) {
                    p.x = -1f;
                    p.y = -1f;
                    repaint.run();
                    return true;
                }
            }
            for (Point p : points) {
                if (p.placed()) continue;
                p.x = Math.max(0f, Math.min(1f, event.getX() / width));
                p.y = Math.max(0f, Math.min(1f, event.getY() / height));
                repaint.run();
                return true;
            }
            return true;
        });

        TextView hint = new TextView(context);
        hint.setText(R.string.tag_placement_hint);
        hint.setTextColor(0xFF9E9E9E);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(context, 10), 0, 0);
        root.addView(hint);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        TextView clear = new TextView(context);
        clear.setText(R.string.tag_placement_clear);
        clear.setTextColor(Color.WHITE);
        clear.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));
        clear.setOnClickListener(v -> {
            for (Point p : points) {
                p.x = -1f;
                p.y = -1f;
            }
            repaint.run();
        });
        buttons.addView(clear);

        TextView done = new TextView(context);
        done.setText(R.string.tag_placement_done);
        done.setTextColor(Color.WHITE);
        done.setTypeface(done.getTypeface(), android.graphics.Typeface.BOLD);
        done.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));
        done.setOnClickListener(v -> {
            float[] outX = new float[points.size()];
            float[] outY = new float[points.size()];
            for (int i = 0; i < points.size(); i++) {
                outX[i] = points.get(i).placed() ? points.get(i).x : -1f;
                outY[i] = points.get(i).placed() ? points.get(i).y : -1f;
            }
            Intent out = new Intent();
            out.putExtra(EXTRA_XS, outX);
            out.putExtra(EXTRA_YS, outY);
            setResult(RESULT_OK, out);
            finish();
        });
        buttons.addView(done);
        root.addView(buttons);

        setContentView(root);
        repaint.run();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
