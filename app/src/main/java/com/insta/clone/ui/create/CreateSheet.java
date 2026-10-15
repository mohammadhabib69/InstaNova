package com.insta.clone.ui.create;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.clone.R;
import com.insta.clone.util.Constants;
import com.insta.clone.util.ToastUtil;

/**
 * PAGE 8A — the Create sheet.
 *
 * Presentation only: every row launches an existing flow, so there is no new
 * navigation target. Story highlight / Live / Made for you have no backing
 * feature in this codebase and follow the shared Constants.DECORATIVE_UI
 * contract (visible + "Not available" toast, hidden when the flag is off).
 */
public final class CreateSheet {

    private CreateSheet() {}

    public static void show(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.InstagramBottomSheetDialog);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_create, null);
        dialog.setContentView(sheet);

        // Existing flows
        View reel = sheet.findViewById(R.id.row_create_reel);
        View post = sheet.findViewById(R.id.row_create_post);
        View story = sheet.findViewById(R.id.row_create_story);
        View highlight = sheet.findViewById(R.id.row_create_highlight);

        reel.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, CreateReelActivity.class));
        });

        post.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, CreatePostActivity.class));
        });

        story.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, CreateStoryActivity.class));
        });

        // "Story highlight" — there is no New highlight flow in the app
        highlight.setOnClickListener(v -> ToastUtil.showNotAvailable(context));

        // Design-only rows
        int[] decorative = {R.id.row_create_live, R.id.row_create_made_for_you};
        for (int id : decorative) {
            View row = sheet.findViewById(id);
            if (row == null) continue;
            if (Constants.DECORATIVE_UI) {
                row.setVisibility(View.VISIBLE);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    ToastUtil.showNotAvailable(context);
                });
            } else {
                row.setVisibility(View.GONE);
            }
        }

        dialog.show();
    }
}
