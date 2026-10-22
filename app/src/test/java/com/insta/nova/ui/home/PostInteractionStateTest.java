package com.insta.nova.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.insta.nova.data.model.Post;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Regression cover for the feed save/unsave defect: interactions must target the post
 * the user actually tapped, survive a list refresh or row recycling, and produce a new
 * list entry so DiffUtil repaints the bookmark.
 */
public class PostInteractionStateTest {

    private static Post post(String id, boolean saved) {
        Post post = new Post();
        post.setId(id);
        post.setUsername("u-" + id);
        post.setSavedByMe(saved);
        post.setLikesCount(3);
        return post;
    }

    private static List<Post> feed() {
        return new ArrayList<>(Arrays.asList(post("p1", false), post("p2", true), post("p3", false)));
    }

    @Test
    public void savingTargetsOnlyTheTappedPost() {
        List<Post> feed = feed();
        Post target = PostInteractionState.findById(feed, "p2");

        List<Post> after = PostInteractionState.replace(
                feed, PostInteractionState.withSaved(target, true));

        assertTrue(PostInteractionState.findById(after, "p2").isSavedByMe());
        assertTrue("p2 was already saved and must stay saved",
                PostInteractionState.findById(after, "p2").isSavedByMe());
        assertFalse(PostInteractionState.findById(after, "p1").isSavedByMe());
        assertFalse(PostInteractionState.findById(after, "p3").isSavedByMe());
    }

    @Test
    public void unsavingTargetsOnlyTheTappedPost() {
        List<Post> feed = feed();
        Post target = PostInteractionState.findById(feed, "p2");

        List<Post> after = PostInteractionState.replace(
                feed, PostInteractionState.withSaved(target, false));

        assertFalse(PostInteractionState.findById(after, "p2").isSavedByMe());
        assertEquals("other rows untouched", 3, after.size());
    }

    @Test
    public void unknownIdNeverTouchesAnotherPost() {
        List<Post> feed = feed();
        Post stranger = post("p9", true);

        List<Post> after = PostInteractionState.replace(feed, stranger);

        assertEquals(3, after.size());
        assertFalse(PostInteractionState.findById(after, "p1").isSavedByMe());
        assertTrue(PostInteractionState.findById(after, "p2").isSavedByMe());
        assertFalse(PostInteractionState.findById(after, "p3").isSavedByMe());
    }

    @Test
    public void replacementIsANewListWithANewInstance() {
        List<Post> feed = feed();
        Post p1 = PostInteractionState.findById(feed, "p1");
        Post updated = PostInteractionState.withSaved(p1, true);

        List<Post> after = PostInteractionState.replace(feed, updated);

        assertNotSame("DiffUtil needs a new list", feed, after);
        assertNotSame("DiffUtil needs a new item", p1, PostInteractionState.findById(after, "p1"));
        assertSame(updated, PostInteractionState.findById(after, "p1"));
        assertSame("untouched rows keep their identity",
                feed.get(1), PostInteractionState.findById(after, "p2"));
    }

    @Test
    public void rapidTapsConvergeOnTheAuthoritativeState() {
        // Two taps in a row: the second must read the state produced by the first,
        // not the stale object the row was bound with.
        List<Post> feed = feed();
        Post stale = PostInteractionState.findById(feed, "p1");

        boolean first = PostInteractionState.nextSaveState(
                PostInteractionState.findById(feed, "p1"));
        feed = PostInteractionState.replace(feed, PostInteractionState.withSaved(stale, first));

        boolean second = PostInteractionState.nextSaveState(
                PostInteractionState.findById(feed, "p1"));
        feed = PostInteractionState.replace(
                feed, PostInteractionState.withSaved(PostInteractionState.findById(feed, "p1"), second));

        assertTrue("first tap saves", first);
        assertFalse("second tap unsaves", second);
        assertFalse(PostInteractionState.findById(feed, "p1").isSavedByMe());
    }

    @Test
    public void rollbackRestoresThePreRequestState() {
        List<Post> feed = feed();
        Post original = PostInteractionState.findById(feed, "p1");
        boolean before = original.isSavedByMe();

        // optimistic save
        List<Post> optimistic = PostInteractionState.replace(
                feed, PostInteractionState.withSaved(original, true));
        assertTrue(PostInteractionState.findById(optimistic, "p1").isSavedByMe());

        // request failed -> revert to the value captured before the request
        Post latest = PostInteractionState.findById(optimistic, "p1");
        List<Post> reverted = PostInteractionState.replace(
                optimistic, PostInteractionState.withSaved(latest, before));

        assertFalse(PostInteractionState.findById(reverted, "p1").isSavedByMe());
    }

    @Test
    public void lookupsSurviveRecyclingAndRefresh() {
        List<Post> feed = feed();
        Post refreshed = post("p2", false);
        feed = PostInteractionState.replace(feed, refreshed);

        assertSame("the refreshed instance is the current one",
                refreshed, PostInteractionState.findById(feed, "p2"));
        assertTrue("a refreshed unsaved row toggles to saved",
                PostInteractionState.nextSaveState(
                        PostInteractionState.findById(feed, "p2")));
    }

    @Test
    public void missingOrNullInputIsSafe() {
        assertTrue(PostInteractionState.replace(null, post("p1", true)).isEmpty());
        assertEquals(null, PostInteractionState.findById(null, "p1"));
        assertEquals(null, PostInteractionState.findById(feed(), null));
        assertEquals(null, PostInteractionState.findById(feed(), "absent"));
        assertTrue(PostInteractionState.nextSaveState(null));
    }
}
