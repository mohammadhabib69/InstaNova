package com.insta.nova.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.model.Comment;
import com.insta.nova.data.remote.RetrofitClient;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.CommentDto;
import com.insta.nova.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository handling comments on posts and comment-likes.
 */
public class CommentRepository {

    private final RestApi restApi;
    private final SessionManager sessionManager;

    public CommentRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.restApi = client.getRestApi();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public LiveData<Resource<List<Comment>>> getComments(String postId) {
        MutableLiveData<Resource<List<Comment>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        restApi.getComments("eq." + postId, "created_at.asc").enqueue(new Callback<List<CommentDto>>() {
            @Override
            public void onResponse(Call<List<CommentDto>> call, Response<List<CommentDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Comment> comments = new ArrayList<>();
                    for (CommentDto dto : response.body()) {
                        comments.add(dto.toDomain());
                    }
                    result.postValue(Resource.success(comments));
                } else {
                    result.postValue(Resource.error("Failed to load comments", null));
                }
            }

            @Override
            public void onFailure(Call<List<CommentDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<Comment>> addComment(String postId, String text) {
        MutableLiveData<Resource<Comment>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("post_id", postId);
        body.put("user_id", myId);
        body.put("body", text.trim());

        restApi.addComment(body).enqueue(new Callback<List<CommentDto>>() {
            @Override
            public void onResponse(Call<List<CommentDto>> call, Response<List<CommentDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Comment comment = response.body().get(0).toDomain();
                    comment.setUsername(sessionManager.getUsername());
                    comment.setAvatarUrl(sessionManager.getAvatarUrl());
                    result.postValue(Resource.success(comment));
                } else {
                    result.postValue(Resource.error("Failed to post comment", null));
                }
            }

            @Override
            public void onFailure(Call<List<CommentDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<Void>> deleteComment(String commentId) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        restApi.deleteComment("eq." + commentId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    result.postValue(Resource.success(null));
                } else {
                    result.postValue(Resource.error("Failed to delete comment", null));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<Void>> toggleCommentLike(String commentId, boolean isLiked) {
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        if (isLiked) {
            Map<String, Object> body = new HashMap<>();
            body.put("comment_id", commentId);
            body.put("user_id", myId);
            restApi.likeComment(body).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) result.postValue(Resource.success(null));
                    else result.postValue(Resource.error("Error liking comment", null));
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    result.postValue(Resource.error("Network error", null));
                }
            });
        } else {
            restApi.unlikeComment("eq." + commentId, "eq." + myId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) result.postValue(Resource.success(null));
                    else result.postValue(Resource.error("Error unliking comment", null));
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    result.postValue(Resource.error("Network error", null));
                }
            });
        }

        return result;
    }
}
