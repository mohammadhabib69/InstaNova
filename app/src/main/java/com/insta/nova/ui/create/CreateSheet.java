package com.insta.nova.ui.create;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.local.LocalSocialStore;
import com.insta.nova.ui.live.LiveActivity;
import com.insta.nova.ui.profile.HighlightCreateSheet;

/**
 * PAGE 8A — the Create sheet.
 *
 * Presentation only: every row launches an existing flow, so there is no new
 * navigation target here. "Made for you" opens the local template gallery and
 * "Story highlight" opens the same creator sheet the profile uses.
 *
 * Live opens the local simulation in {@link LiveActivity}: camera preview only,
 * with fake viewers and comments. No backend is involved.
 */
public final class CreateSheet {

    private CreateSheet() {}

    public static void show(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.InstaNovaBottomSheetDialog);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_create, null);
        dialog.setContentView(sheet);

        // Existing flows
        View reel = sheet.findViewById(R.id.row_create_reel);
        View post = sheet.findViewById(R.id.row_create_post);
        View story = sheet.findViewById(R.id.row_create_story);
        View highlight = sheet.findViewById(R.id.row_create_highlight);
        View live = sheet.findViewById(R.id.row_create_live);

        reel.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, NewReelActivity.class));
        });

        // Same flow as the bottom-navigation "+" item: media selection -> edit -> details
        // -> caption. NewPostActivity is a separate quick-post path and must not be used here.
        post.setOnClickListener(v -> {
            dialog.dismiss();
            CreatePostActivity.start(context);
        });

        // Same flow as the profile "create story" entry point: media selection -> story
        // editor -> share. NewStoryActivity is the quick picker-only path and is not used
        // here.
        story.setOnClickListener(v -> {
            dialog.dismiss();
            CreateStoryActivity.start(context, null);
        });

        live.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, LiveActivity.class));
        });

        // "Story highlight": same creator the profile uses, with its empty state.
        highlight.setOnClickListener(v -> {
            dialog.dismiss();
            if (!(context instanceof FragmentActivity)) return;
            FragmentActivity activity = (FragmentActivity) context;
            LocalSocialStore store = new LocalSocialStore(activity);
            if (store.getPastStories().isEmpty()) {
                new AlertDialog.Builder(activity)
                        .setMessage(R.string.highlight_no_stories)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                new HighlightCreateSheet()
                        .show(activity.getSupportFragmentManager(), "highlight_create");
            }
        });

        // "Made for you": four local template cards, no network involved.
        View madeForYou = sheet.findViewById(R.id.row_create_made_for_you);
        if (madeForYou != null) {
            madeForYou.setVisibility(View.VISIBLE);
            madeForYou.setOnClickListener(v -> {
                dialog.dismiss();
                context.startActivity(new Intent(context, TemplatesActivity.class));
            });
        }

        dialog.show();
    }
}
