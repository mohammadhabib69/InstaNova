package com.insta.clone.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonElement;
import com.insta.clone.InstaApp;
import com.insta.clone.data.local.SessionManager;
import com.insta.clone.data.model.Conversation;
import com.insta.clone.data.model.Message;
import com.insta.clone.data.remote.RetrofitClient;
import com.insta.clone.data.remote.api.RestApi;
import com.insta.clone.data.remote.dto.ConversationDto;
import com.insta.clone.data.remote.dto.MessageDto;
import com.insta.clone.data.remote.dto.UnreadCountsDto;
import com.insta.clone.util.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for direct message conversations and chat messages backed by Supabase.
 */
public class ChatRepository {

    private static ChatRepository instance;
    private final RestApi restApi;
    private final SessionManager sessionManager;

    public ChatRepository(Context context) {
        RetrofitClient client = RetrofitClient.getInstance(context);
        this.restApi = client.getRestApi();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public static synchronized ChatRepository getInstance() {
        if (instance == null) {
            instance = new ChatRepository(InstaApp.getInstance());
        }
        return instance;
    }

    public static synchronized ChatRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ChatRepository(context.getApplicationContext());
        }
        return instance;
    }

    public LiveData<Resource<List<Conversation>>> getConversations() {
        MutableLiveData<Resource<List<Conversation>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        restApi.getInbox(new HashMap<>()).enqueue(new Callback<List<ConversationDto>>() {
            @Override
            public void onResponse(Call<List<ConversationDto>> call, Response<List<ConversationDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Conversation> list = new ArrayList<>();
                    for (ConversationDto dto : response.body()) {
                        list.add(dto.toConversation());
                    }
                    result.postValue(Resource.success(list));
                } else {
                    result.postValue(Resource.error("Failed to load inbox", null));
                }
            }

            @Override
            public void onFailure(Call<List<ConversationDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<String>> getOrCreateConversation(String otherUserId) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, Object> body = new HashMap<>();
        body.put("p_other_user_id", otherUserId);

        restApi.getOrCreateConversation(body).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String conversationId;
                    try {
                        conversationId = response.body().getAsString();
                    } catch (Exception e) {
                        conversationId = response.body().toString().replace("\"", "");
                    }
                    result.postValue(Resource.success(conversationId));
                } else {
                    result.postValue(Resource.error("Failed to start conversation", null));
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<List<Message>>> getMessages(String conversationId) {
        MutableLiveData<Resource<List<Message>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Map<String, String> query = new HashMap<>();
        query.put("conversation_id", "eq." + conversationId);
        query.put("order", "created_at.asc");

        restApi.getMessages(query).enqueue(new Callback<List<MessageDto>>() {
            @Override
            public void onResponse(Call<List<MessageDto>> call, Response<List<MessageDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Message> list = new ArrayList<>();
                    for (MessageDto dto : response.body()) {
                        list.add(dto.toMessage());
                    }
                    result.postValue(Resource.success(list));
                } else {
                    result.postValue(Resource.error("Failed to load messages", null));
                }
            }

            @Override
            public void onFailure(Call<List<MessageDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public LiveData<Resource<Message>> sendMessage(String conversationId, String text, String mediaUrl) {
        MutableLiveData<Resource<Message>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        String myId = sessionManager.getUserId();
        if (myId == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("conversation_id", conversationId);
        body.put("sender_id", myId);
        body.put("body", text != null && !text.isEmpty() ? text : "Sent a photo");
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            body.put("media_url", mediaUrl);
        }

        restApi.sendMessage(body).enqueue(new Callback<List<MessageDto>>() {
            @Override
            public void onResponse(Call<List<MessageDto>> call, Response<List<MessageDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    result.postValue(Resource.success(response.body().get(0).toMessage()));
                } else {
                    result.postValue(Resource.error("Failed to send message", null));
                }
            }

            @Override
            public void onFailure(Call<List<MessageDto>> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return result;
    }

    public void markMessagesRead(String conversationId) {
        String myId = sessionManager.getUserId();
        if (myId == null) return;

        Map<String, String> query = new HashMap<>();
        query.put("conversation_id", "eq." + conversationId);
        query.put("sender_id", "neq." + myId);
        query.put("is_read", "eq.false");

        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);

        restApi.markMessagesRead(query, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {}

            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    public LiveData<Resource<UnreadCountsDto>> getUnreadCounts() {
        MutableLiveData<Resource<UnreadCountsDto>> result = new MutableLiveData<>();
        restApi.getUnreadCounts(new HashMap<>()).enqueue(new Callback<UnreadCountsDto>() {
            @Override
            public void onResponse(Call<UnreadCountsDto> call, Response<UnreadCountsDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    result.postValue(Resource.success(response.body()));
                } else {
                    result.postValue(Resource.error("Failed to fetch unread counts", null));
                }
            }

            @Override
            public void onFailure(Call<UnreadCountsDto> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return result;
    }
}
