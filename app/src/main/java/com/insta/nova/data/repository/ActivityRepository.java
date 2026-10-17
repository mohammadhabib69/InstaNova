package com.insta.nova.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.insta.nova.data.remote.dto.UnreadCountsDto;
import com.insta.nova.util.Resource;

/**
 * Notification and inbox badge counts.
 *
 * <p>This no longer performs its own request: the counters come from the single
 * shared request owned by {@link ChatRepository}, so the 30 s badge poll and the
 * Home screen cannot issue duplicate calls for the same data.
 */
public class ActivityRepository {

    public static class UnreadCounts {
        private final int notifications; private final int directMessages;
        public UnreadCounts(int notifications, int directMessages) { this.notifications = notifications; this.directMessages = directMessages; }
        public int getNotifications() { return notifications; }
        public int getDirectMessages() { return directMessages; }
    }

    private final ChatRepository chatRepository;

    public ActivityRepository(Context context) {
        chatRepository = ChatRepository.getInstance(context);
    }

    /** Observer view over the shared stream. Attach once, not per poll tick. */
    public LiveData<Resource<UnreadCounts>> unreadCounts() {
        return Transformations.map(chatRepository.unreadCounts(), dto -> {
            if (dto == null) return null;
            if (dto.status == Resource.Status.SUCCESS) {
                UnreadCountsDto data = dto.data;
                return Resource.success(data == null
                        ? new UnreadCounts(0, 0)
                        : new UnreadCounts(data.getNotifications(), data.getDirectMessages()));
            }
            if (dto.status == Resource.Status.ERROR) {
                return Resource.<UnreadCounts>error(dto.message);
            }
            return Resource.<UnreadCounts>loading();
        });
    }

    /** @see ChatRepository#refreshUnreadCounts(boolean) */
    public boolean refreshUnreadCounts(boolean force) {
        return chatRepository.refreshUnreadCounts(force);
    }
}
