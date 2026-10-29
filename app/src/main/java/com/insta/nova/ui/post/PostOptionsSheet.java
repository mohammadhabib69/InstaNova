package com.insta.nova.ui.post;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Post;
import com.insta.nova.data.repository.PostRepository;
import com.insta.nova.databinding.DialogPostOptionsBinding;
import com.insta.nova.util.Constants;
import com.insta.nova.util.Resource;

/**
 * Own-post options: Edit caption, Archive, Delete, Share, Copy link. Each action
 * writes to the real post row; nothing here is a placeholder.
 */
public final class PostOptionsSheet {

    private PostOptionsSheet() {}

    public interface Callback {
        void onChanged();
        void onDeleted();
        default void onArchived() { onChanged(); }
    }

    public static void show(Context context, Post post, Callback callback) {
        if (post == null || post.getId() == null) return;
        String myId = SessionManager.getInstance(context).getUserId();
        boolean mine = myId != null && myId.equals(post.getUserId());

        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.InstaNovaBottomSheetDialog);
        DialogPostOptionsBinding binding = DialogPostOptionsBinding.inflate(
                android.view.LayoutInflater.from(context));
        PostRepository repository = new PostRepository(context);

        binding.btnEditCaption.setVisibility(mine ? View.VISIBLE : View.GONE);
        binding.btnArchivePost.setVisibility(mine ? View.VISIBLE : View.GONE);
        binding.btnDeletePost.setVisibility(mine ? View.VISIBLE : View.GONE);
        binding.btnUnfollow.setVisibility(View.GONE);

        binding.btnSharePost.setOnClickListener(v -> {
            dialog.dismiss();
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, Constants.PROFILE_URL_BASE + "p/" + post.getId());
            context.startActivity(Intent.createChooser(share, context.getString(R.string.st_share)));
        });

        binding.btnCopyLink.setOnClickListener(v -> {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("post",
                        Constants.PROFILE_URL_BASE + "p/" + post.getId()));
                Toast.makeText(context, R.string.st_link_copied, Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        binding.btnEditCaption.setOnClickListener(v -> {
            dialog.dismiss();
            showCaptionDialog(context, post, repository, callback);
        });

        binding.btnArchivePost.setOnClickListener(v -> {
            dialog.dismiss();
            repository.setArchived(post.getId(), true).observeForever(resource -> {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    Toast.makeText(context, R.string.st_archived_toast, Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onArchived();
                } else {
                    Toast.makeText(context, R.string.st_something_went_wrong, Toast.LENGTH_LONG).show();
                }
            });
        });

        binding.btnDeletePost.setOnClickListener(v -> {
            dialog.dismiss();
            new AlertDialog.Builder(context)
                    .setTitle(R.string.st_delete_post_title)
                    .setMessage(R.string.st_delete_post_body)
                    .setNegativeButton(R.string.st_cancel, null)
                    .setPositiveButton(R.string.st_delete_item, (d, w) ->
                            repository.deletePost(post.getId()).observeForever(resource -> {
                                if (resource == null || resource.status == Resource.Status.LOADING) return;
                                if (resource.status == Resource.Status.SUCCESS) {
                                    Toast.makeText(context, R.string.st_deleted_toast, Toast.LENGTH_SHORT).show();
                                    if (callback != null) callback.onDeleted();
                                } else {
                                    Toast.makeText(context, R.string.st_something_went_wrong, Toast.LENGTH_LONG).show();
                                }
                            }))
                    .show();
        });

        dialog.setContentView(binding.getRoot());
        dialog.show();
    }

    private static void showCaptionDialog(Context context, Post post, PostRepository repository,
                                          Callback callback) {
        int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(context);
        container.setPadding(pad, pad / 2, pad, 0);
        EditText input = new EditText(context);
        input.setText(post.getCaption() == null ? "" : post.getCaption());
        input.setHint(R.string.st_caption_hint);
        input.setSelection(input.getText().length());
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.st_edit_caption)
                .setView(container)
                .setNegativeButton(R.string.st_cancel, null)
                .setPositiveButton(R.string.st_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String caption = input.getText().toString().trim();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            repository.updateCaption(post.getId(), caption).observeForever(resource -> {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    post.setCaption(caption);
                    Toast.makeText(context, R.string.st_caption_saved, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    if (callback != null) callback.onChanged();
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    Toast.makeText(context, R.string.st_caption_failed, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
        input.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }
}
