package com.insta.clone.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.insta.clone.data.model.Conversation;
import com.insta.clone.data.model.Message;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for direct message conversations and chat messages.
 */
public class ChatRepository {
    private static ChatRepository instance;
    private final MutableLiveData<List<Conversation>> conversationsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final Map<String, MutableLiveData<List<Message>>> messageMaps = new HashMap<>();

    private ChatRepository() {}

    public static synchronized ChatRepository getInstance() {
        if (instance == null) {
            instance = new ChatRepository();
        }
        return instance;
    }

    public LiveData<List<Conversation>> getConversations() {
        return conversationsLiveData;
    }

    public LiveData<List<Message>> getMessages(String conversationId) {
        if (!messageMaps.containsKey(conversationId)) {
            messageMaps.put(conversationId, new MutableLiveData<>(new ArrayList<>()));
        }
        return messageMaps.get(conversationId);
    }

    public void sendMessage(String conversationId, Message message) {
        if (!messageMaps.containsKey(conversationId)) {
            messageMaps.put(conversationId, new MutableLiveData<>(new ArrayList<>()));
        }
        MutableLiveData<List<Message>> live = messageMaps.get(conversationId);
        List<Message> list = live.getValue();
        if (list == null) list = new ArrayList<>();
        list.add(message);
        live.setValue(new ArrayList<>(list));

        // Update conversation last message
        List<Conversation> convs = conversationsLiveData.getValue();
        if (convs != null) {
            for (Conversation c : convs) {
                if (c.getId().equals(conversationId)) {
                    c.setLastMessage(message.getText());
                    c.setLastMessageTimestamp(message.getCreatedAt());
                }
            }
            conversationsLiveData.setValue(new ArrayList<>(convs));
        }
    }
}
