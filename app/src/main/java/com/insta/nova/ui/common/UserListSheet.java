package com.insta.nova.ui.common;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.insta.nova.R;
import com.insta.nova.data.model.Profile;
import com.insta.nova.util.GlideUtils;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable user-list bottom sheet built entirely from the existing
 * {@code layout_new_message_sheet} + {@code item_user_row} resources.
 *
 * It is the single implementation behind the Inbox "New message" list, the
 * post "Liked by" list, the followers / following lists and the
 * "Send post to…" share sheet, so no new visual design is introduced.
 */
public final class UserListSheet {

    public interface ProfileLoader {
        LiveData<Resource<List<Profile>>> load(@Nullable String query);
    }

    public interface OnProfileClick {
        void onClick(Profile profile);
    }

    private static final long SEARCH_DEBOUNCE_MS = 400L;

    private UserListSheet() {}

    public static void show(@NonNull Fragment fragment,
                            @NonNull String title,
                            boolean searchable,
                            @Nullable String searchHint,
                            @NonNull ProfileLoader loader,
                            @NonNull OnProfileClick onClickListener) {
        show(fragment.requireContext(), fragment.getViewLifecycleOwner(), title, searchable, searchHint, loader, onClickListener);
    }

    /** Activity entry point (e.g. StoryViewerActivity's viewers list). */
    public static void show(@NonNull androidx.appcompat.app.AppCompatActivity activity,
                            @NonNull String title,
                            boolean searchable,
                            @Nullable String searchHint,
                            @NonNull ProfileLoader loader,
                            @NonNull OnProfileClick onClickListener) {
        show(activity, activity, title, searchable, searchHint, loader, onClickListener);
    }

    private static void show(@NonNull Context context,
                            @NonNull LifecycleOwner owner,
                            @NonNull String title,
                            boolean searchable,
                            @Nullable String searchHint,
                            @NonNull ProfileLoader loader,
                            @NonNull OnProfileClick onClickListener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.InstaNovaBottomSheetDialog);
        View sheet = LayoutInflater.from(context).inflate(R.layout.layout_new_message_sheet, null);
        dialog.setContentView(sheet);

        TextView tvTitle = sheet.findViewById(R.id.tv_sheet_title);
        if (tvTitle != null) tvTitle.setText(title);

        ImageView ivClose = sheet.findViewById(R.id.iv_close);
        if (ivClose != null) ivClose.setOnClickListener(v -> dialog.dismiss());

        EditText etSearch = sheet.findViewById(R.id.et_search);
        ProgressBar pbLoading = sheet.findViewById(R.id.pb_loading);
        RecyclerView rvUsers = sheet.findViewById(R.id.rv_users);
        TextView tvEmpty = new TextView(context);
        tvEmpty.setText(R.string.search_no_results);
        android.util.TypedValue muted = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.themeTextSecondary, muted, true)) {
            tvEmpty.setTextColor(muted.data);
        } else {
            tvEmpty.setTextColor(ContextCompat.getColor(context, R.color.color_icon_muted));
        }
        tvEmpty.setTextSize(14f);
        tvEmpty.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        tvEmpty.setVisibility(View.GONE);
        if (rvUsers != null && rvUsers.getParent() instanceof ViewGroup) {
            ((ViewGroup) rvUsers.getParent()).addView(tvEmpty);
        }

        RowAdapter adapter = new RowAdapter(
                profile -> {
                    dialog.dismiss();
                    onClickListener.onClick(profile);
                });
        if (rvUsers != null) {
            rvUsers.setLayoutManager(new LinearLayoutManager(context));
            rvUsers.setAdapter(adapter);
        }

        final Observer<Resource<List<Profile>>> observer = resource -> {
            if (resource == null) return;
            if (pbLoading != null) {
                pbLoading.setVisibility(resource.status == Resource.Status.LOADING ? View.VISIBLE : View.GONE);
            }
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                adapter.submit(resource.data);
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(resource.data.isEmpty() ? View.VISIBLE : View.GONE);
                }
            } else if (resource.status == Resource.Status.ERROR) {
                adapter.submit(new ArrayList<>());
                if (tvEmpty != null) {
                    tvEmpty.setText(resource.message == null ? context.getString(R.string.search_no_results) : resource.message);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }
        };

        final LiveData<Resource<List<Profile>>>[] current = new LiveData[]{null};
        final Handler handler = new Handler(Looper.getMainLooper());

        final Runnable loadInitial = () -> {
            if (current[0] != null) current[0].removeObserver(observer);
            LiveData<Resource<List<Profile>>> live = loader.load(null);
            current[0] = live;
            live.observe(owner, observer);
        };

        if (etSearch != null) {
            if (searchable) {
                if (searchHint != null) etSearch.setHint(searchHint);
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(Editable s) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                        handler.removeCallbacksAndMessages(null);
                        final String query = s == null ? "" : s.toString().trim();
                        handler.postDelayed(() -> {
                            if (current[0] != null) current[0].removeObserver(observer);
                            LiveData<Resource<List<Profile>>> live = loader.load(query);
                            current[0] = live;
                            live.observe(owner, observer);
                        }, SEARCH_DEBOUNCE_MS);
                    }
                });
            } else {
                etSearch.setVisibility(View.GONE);
            }
        }

        dialog.setOnDismissListener(d -> {
            handler.removeCallbacksAndMessages(null);
            if (current[0] != null) current[0].removeObserver(observer);
        });

        dialog.show();
        loadInitial.run();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Row adapter over the existing item_user_row layout. */
    private static final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

        private final List<Profile> items = new ArrayList<>();
        private final OnProfileClick listener;

        RowAdapter(OnProfileClick listener) {
            this.listener = listener;
        }

        void submit(List<Profile> profiles) {
            items.clear();
            if (profiles != null) items.addAll(profiles);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_row, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Profile profile = items.get(position);
            holder.tvUsername.setText(profile.getUsername() == null ? "" : profile.getUsername());

            String fullName = profile.getFullName();
            if (fullName == null || fullName.trim().isEmpty()) {
                holder.tvFullName.setText("");
                holder.tvFullName.setVisibility(View.GONE);
            } else {
                holder.tvFullName.setText(fullName);
                holder.tvFullName.setVisibility(View.VISIBLE);
            }

            GlideUtils.loadAvatar(holder.ivAvatar, profile.getAvatarUrl());
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(profile);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvUsername;
            final TextView tvFullName;

            Holder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvUsername = itemView.findViewById(R.id.tv_username);
                tvFullName = itemView.findViewById(R.id.tv_full_name);
            }
        }
    }
}
