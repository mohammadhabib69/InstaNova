package com.insta.nova.ui.home;

import androidx.annotation.Nullable;

import com.insta.nova.data.model.StoryGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the visible story tray without making the signed-in user depend on the
 * active-story RPC. The owner row is presentation state backed by the real
 * profile; it is not a story record.
 */
public final class StoryTrayOrdering {

    private StoryTrayOrdering() {}

    public static List<StoryGroup> ownerFirst(@Nullable List<StoryGroup> source,
                                              @Nullable String ownerId,
                                              @Nullable String ownerUsername,
                                              @Nullable String ownerAvatar) {
        List<StoryGroup> result = new ArrayList<>();
        String owner = normalize(ownerId);
        if (owner.isEmpty()) return result;

        StoryGroup ownerGroup = null;
        Set<String> seen = new HashSet<>();
        if (source != null) {
            for (StoryGroup candidate : source) {
                if (candidate == null) continue;
                String userId = normalize(candidate.getUserId());
                if (userId.isEmpty() || !seen.add(userId)) continue;
                if (userId.equals(owner)) {
                    ownerGroup = candidate;
                } else {
                    candidate.setOwner(false);
                    result.add(candidate);
                }
            }
        }

        if (ownerGroup == null) {
            ownerGroup = new StoryGroup(owner, safe(ownerUsername), safe(ownerAvatar),
                    true, false, new ArrayList<>());
            ownerGroup.setHasActiveStory(false);
        } else {
            ownerGroup.setOwner(true);
            ownerGroup.setHasActiveStory(true);
            if (isBlank(ownerGroup.getUsername())) ownerGroup.setUsername(safe(ownerUsername));
            if (isBlank(ownerGroup.getAvatarUrl())) ownerGroup.setAvatarUrl(safe(ownerAvatar));
        }
        result.add(0, ownerGroup);
        return result;
    }

    @Nullable
    public static StoryGroup findOwner(@Nullable List<StoryGroup> groups, @Nullable String ownerId) {
        String owner = normalize(ownerId);
        if (owner.isEmpty() || groups == null) return null;
        for (StoryGroup group : groups) {
            if (group != null && owner.equals(normalize(group.getUserId()))) return group;
        }
        return null;
    }

    public static boolean isOwner(@Nullable String viewerId, @Nullable String userId) {
        String viewer = normalize(viewerId);
        return !viewer.isEmpty() && viewer.equals(normalize(userId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
