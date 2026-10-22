package com.insta.nova.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The feed updates a post by replacing it with a copy, never by mutating the instance
 * the adapter is already holding — otherwise the RecyclerView diff compares an object
 * with itself and skips the rebind, so a like is saved but never drawn.
 */
public class PostCopyTest {

    private Post sample() {
        Post post = new Post();
        post.setId("p1");
        post.setUserId("u1");
        post.setUsername("instanova11");
        post.setAvatarUrl("https://example.test/a.jpg");
        post.setVerified(true);
        post.setCaption("caption");
        post.setLocation("Dhaka");
        post.setMediaUrls(new ArrayList<>(Arrays.asList("m1", "m2")));
        post.setMediaType("image");
        post.setLikesCount(4);
        post.setCommentsCount(2);
        post.setLikedByMe(false);
        post.setSavedByMe(true);
        post.setVideoUrl("v.mp4");
        post.setThumbnailUrl("t.jpg");
        post.setCreatedAt(1_700_000_000_000L);
        return post;
    }

    @Test
    public void copyCarriesEveryField() {
        Post original = sample();
        Post copy = original.copy();

        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getUserId(), copy.getUserId());
        assertEquals(original.getUsername(), copy.getUsername());
        assertEquals(original.getAvatarUrl(), copy.getAvatarUrl());
        assertEquals(original.isVerified(), copy.isVerified());
        assertEquals(original.getCaption(), copy.getCaption());
        assertEquals(original.getLocation(), copy.getLocation());
        assertEquals(original.getMediaUrls(), copy.getMediaUrls());
        assertEquals(original.getMediaType(), copy.getMediaType());
        assertEquals(original.getLikesCount(), copy.getLikesCount());
        assertEquals(original.getCommentsCount(), copy.getCommentsCount());
        assertEquals(original.isLikedByMe(), copy.isLikedByMe());
        assertEquals(original.isSavedByMe(), copy.isSavedByMe());
        assertEquals(original.getVideoUrl(), copy.getVideoUrl());
        assertEquals(original.getThumbnailUrl(), copy.getThumbnailUrl());
        assertEquals(original.getCreatedAt(), copy.getCreatedAt());
    }

    @Test
    public void copyIsADistinctInstanceWithItsOwnMediaList() {
        Post original = sample();
        Post copy = original.copy();

        assertNotSame("DiffUtil needs a different instance", original, copy);
        assertNotSame("Media list must not be shared", original.getMediaUrls(), copy.getMediaUrls());

        copy.setLikedByMe(true);
        copy.setLikesCount(5);
        copy.getMediaUrls().add("m3");

        assertFalse("The original must keep its pre-toggle state", original.isLikedByMe());
        assertEquals(4, original.getLikesCount());
        assertEquals(2, original.getMediaUrls().size());
        assertTrue(copy.isLikedByMe());
    }

    @Test
    public void copyOfNullIsUsable() {
        Post copy = new Post((Post) null);
        assertTrue(copy.getMediaUrls() != null);
        assertTrue(copy.getMediaUrls().isEmpty());
    }

    @Test
    public void copyStillComparesByContentNotIdentity() {
        List<Post> before = new ArrayList<>();
        before.add(sample());

        Post toggled = before.get(0).copy();
        toggled.setLikedByMe(true);
        toggled.setLikesCount(5);

        // This is the condition the feed's DiffUtil relies on.
        assertFalse(before.get(0).isLikedByMe() == toggled.isLikedByMe()
                && before.get(0).getLikesCount() == toggled.getLikesCount());
    }
}
