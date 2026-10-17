package com.insta.nova.ui.home;

import com.insta.nova.data.model.Post;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure list/state helpers for feed interactions.
 *
 * <p>The feed used to trust the object captured when a row was bound. That object
 * goes stale the moment the list is replaced (a refresh, another interaction, or a
 * rebind after recycling), so a second tap could be computed from the wrong "current"
 * state and target the wrong row. Every interaction now resolves the authoritative
 * item by id, produces a copy, and swaps that copy into a new list — which is also
 * what makes DiffUtil notice the change.
 */
public final class PostInteractionState {

    private PostInteractionState() {
    }

    /** Returns the current entry for {@code id}, or null when it is no longer listed. */
    public static Post findById(List<Post> posts, String id) {
        if (posts == null || id == null) return null;
        for (Post post : posts) {
            if (post != null && id.equals(post.getId())) return post;
        }
        return null;
    }

    /**
     * Returns a new list with only the entry matching {@code updated.getId()} replaced.
     * When the id is absent the list is returned unchanged (never a different post).
     */
    public static List<Post> replace(List<Post> posts, Post updated) {
        List<Post> copy = new ArrayList<>(posts == null ? new ArrayList<>() : posts);
        if (updated == null || updated.getId() == null) return copy;
        for (int i = 0; i < copy.size(); i++) {
            Post candidate = copy.get(i);
            if (candidate != null && updated.getId().equals(candidate.getId())) {
                copy.set(i, updated);
                return copy;
            }
        }
        return copy;
    }

    /** The state a save toggle should move to, read from the authoritative item. */
    public static boolean nextSaveState(Post current) {
        return current == null || !current.isSavedByMe();
    }

    /** A copy of {@code current} carrying {@code saved}. */
    public static Post withSaved(Post current, boolean saved) {
        Post updated = current == null ? new Post() : current.copy();
        updated.setSavedByMe(saved);
        return updated;
    }

    /** A copy of {@code current} carrying the like state and count. */
    public static Post withLiked(Post current, boolean liked, int likesCount) {
        Post updated = current == null ? new Post() : current.copy();
        updated.setLikedByMe(liked);
        updated.setLikesCount(Math.max(0, likesCount));
        return updated;
    }
}
