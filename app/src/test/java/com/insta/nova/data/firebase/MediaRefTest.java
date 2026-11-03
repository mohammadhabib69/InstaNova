package com.insta.nova.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.insta.nova.data.firebase.model.MediaRef;

import org.junit.Test;

import java.util.Map;

/** Media reference contract: parsing, URL rebuilding, ownership (tasks P4-07/08/13). */
public class MediaRefTest {

    private static final String BASE = "https://poqdemxiuoocbzkwbyhy.supabase.co";
    private static final String AVATAR =
            BASE + "/storage/v1/object/public/avatars/3c96a7cc-5c8b-4a79-9138-8c6c9491aed7/4166c987.jpg";

    @Test
    public void parsesAPublicStorageUrlLikeTheExistingRows() {
        MediaRef ref = MediaRef.parse(AVATAR, "posts");
        assertNotNull(ref);
        assertEquals("avatars", ref.bucket);
        assertEquals("3c96a7cc-5c8b-4a79-9138-8c6c9491aed7/4166c987.jpg", ref.path);
        assertEquals("3c96a7cc-5c8b-4a79-9138-8c6c9491aed7", ref.ownerLegacyId);
        assertEquals("image", ref.mediaType);
        assertEquals(AVATAR, ref.url);
    }

    @Test
    public void parsesVideoPathsAndLegacyAuthenticatedUrls() {
        MediaRef video = MediaRef.parse(
                BASE + "/storage/v1/object/authenticated/reels/u1/clip.mp4", "posts");
        assertNotNull(video);
        assertEquals("reels", video.bucket);
        assertEquals("video", video.mediaType);

        MediaRef legacy = MediaRef.parse(BASE + "/object/posts/u1/a.png", "posts");
        assertNull("legacy /object/ shape is handled by MediaUrl, not stored here", legacy);
    }

    @Test
    public void rejectsNonStorageUrls() {
        assertNull(MediaRef.parse("content://media/external/images/1", "posts"));
        assertNull(MediaRef.parse("https://example.com/cat.jpg", "posts"));
        assertNull(MediaRef.parse(null, "posts"));
    }

    @Test
    public void rebuildsTheUrlWhenTheDocumentHasNone() {
        MediaRef ref = new MediaRef();
        ref.bucket = "posts";
        ref.path = "abc/f.jpg";
        assertEquals(BASE + "/storage/v1/object/public/posts/abc/f.jpg", ref.publicUrl(BASE));
    }

    @Test
    public void keepsAnExistingUrlVerbatim() {
        MediaRef ref = MediaRef.parse(AVATAR, "posts");
        assertNotNull(ref);
        assertEquals(AVATAR, ref.publicUrl(BASE));
    }

    @Test
    public void ownershipUsesTheFirstPathSegment() {
        MediaRef ref = MediaRef.parse(AVATAR, "posts");
        assertNotNull(ref);
        assertTrue(ref.isOwnedByLegacyId("3c96a7cc-5c8b-4a79-9138-8c6c9491aed7"));
        assertFalse("prefix-only id must not match", ref.isOwnedByLegacyId("3c96a7cc"));
        assertFalse(ref.isOwnedByLegacyId("89ef8a42-2636-41e6-ae4b-65fa92756841"));
        assertFalse(ref.isOwnedByLegacyId(null));
    }

    @Test
    public void roundTripsThroughAMap() {
        MediaRef ref = MediaRef.parse(AVATAR, "posts");
        assertNotNull(ref);
        Map<String, Object> map = ref.toMap();
        MediaRef back = MediaRef.fromMap(map);
        assertEquals(ref.bucket, back.bucket);
        assertEquals(ref.path, back.path);
        assertEquals(ref.url, back.url);
        assertEquals(ref.mediaType, back.mediaType);
    }

    @Test
    public void emptyMapYieldsSafeDefaults() {
        MediaRef ref = MediaRef.fromMap(null);
        assertEquals("posts", ref.bucket);
        assertEquals("image", ref.mediaType);
        assertFalse(ref.isOwnedByLegacyId("anything"));
    }
}
