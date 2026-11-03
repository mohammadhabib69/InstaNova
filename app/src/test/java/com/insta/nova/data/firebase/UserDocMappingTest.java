package com.insta.nova.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.insta.nova.data.firebase.model.PublicProfileDoc;
import com.insta.nova.data.firebase.model.UserDoc;

import org.junit.Test;

import java.util.Map;

/**
 * Guards the identity document contract (ARCHITECTURE §3, task P2-06).
 *
 * <p>The security-relevant assertion is the last one: the public projection must never
 * carry the fields that audit finding S4 showed leaking (birthday, legacy UUID, FCM
 * tokens). If someone adds a field to {@link PublicProfileDoc}, this test is the tripwire.
 */
public class UserDocMappingTest {

    private UserDoc sample() {
        UserDoc user = new UserDoc();
        user.uid = "firebase-uid-123";
        user.legacyProfileId = "8f14e45f-ceea-467a-9c1e-0b0c1a2d3e4f";
        user.username = "nova_user";
        user.fullName = "Nova User";
        user.bio = "hello";
        user.website = "https://example.com";
        user.avatarUrl = "https://cdn.example/av.jpg";
        user.verified = true;
        user.isPrivate = true;
        user.accountType = "student";
        user.birthday = "1999-01-01";
        user.showActivity = false;
        user.whoCanCall = "following";
        user.createdAt = 1_700_000_000_000L;
        user.updatedAt = 1_700_000_100_000L;
        user.postsCount = 11;
        user.followersCount = 20;
        user.followingCount = 8;
        return user;
    }

    @Test
    public void roundTripPreservesEveryField() {
        UserDoc original = sample();
        UserDoc restored = UserDoc.fromMap(original.toMap());

        assertEquals(original.uid, restored.uid);
        assertEquals(original.legacyProfileId, restored.legacyProfileId);
        assertEquals(original.username, restored.username);
        assertEquals(original.fullName, restored.fullName);
        assertEquals(original.bio, restored.bio);
        assertEquals(original.website, restored.website);
        assertEquals(original.avatarUrl, restored.avatarUrl);
        assertEquals(original.verified, restored.verified);
        assertEquals(original.isPrivate, restored.isPrivate);
        assertEquals(original.accountType, restored.accountType);
        assertEquals(original.birthday, restored.birthday);
        assertEquals(original.showActivity, restored.showActivity);
        assertEquals(original.whoCanCall, restored.whoCanCall);
        assertEquals(original.createdAt, restored.createdAt);
        assertEquals(original.updatedAt, restored.updatedAt);
    }

    @Test
    public void countersAreNestedUnderCountersMap() {
        Map<String, Object> map = sample().toMap();
        Object counters = map.get("counters");
        assertTrue("counters must be a map", counters instanceof Map);
        Map<?, ?> c = (Map<?, ?>) counters;
        assertEquals(11L, ((Number) c.get("posts")).longValue());
        assertEquals(20L, ((Number) c.get("followers")).longValue());
        assertEquals(8L, ((Number) c.get("following")).longValue());
    }

    @Test
    public void fromMapToleratesMissingAndNullValues() {
        UserDoc doc = UserDoc.fromMap(null);
        assertEquals("", doc.username);
        assertFalse(doc.verified);
        assertEquals(0L, doc.createdAt);

        java.util.HashMap<String, Object> partial = new java.util.HashMap<>();
        partial.put("uid", "u1");
        partial.put("isVerified", null);
        UserDoc fromPartial = UserDoc.fromMap(partial);
        assertEquals("u1", fromPartial.uid);
        assertFalse(fromPartial.verified);
    }

    @Test
    public void publicProjectionCarriesOnlyPublicFields() {
        PublicProfileDoc pub = PublicProfileDoc.from(sample());
        Map<String, Object> map = pub.toMap();

        assertEquals("nova_user", map.get("username"));
        assertEquals(Boolean.TRUE, map.get("isVerified"));
        assertEquals(20, map.get("followersCount"));

        assertFalse("birthday must never be public", map.containsKey("birthday"));
        assertFalse("legacy uuid must never be public", map.containsKey("legacyProfileId"));
        assertFalse("fcm tokens must never be public", map.containsKey("fcmTokens"));
        assertFalse("settings must never be public", map.containsKey("settings"));
        assertFalse("account type must never be public", map.containsKey("accountType"));
        assertFalse("whoCanCall must never be public", map.containsKey("whoCanCall"));
    }

    @Test
    public void publicProjectionRoundTrip() {
        PublicProfileDoc original = PublicProfileDoc.from(sample());
        PublicProfileDoc restored = PublicProfileDoc.fromMap(original.toMap());
        assertEquals(original.uid, restored.uid);
        assertEquals(original.username, restored.username);
        assertEquals(original.followersCount, restored.followersCount);
        assertEquals(original.isPrivate, restored.isPrivate);
    }

    @Test
    public void publicProjectionCarriesCaseInsensitiveUsernameKey() {
        UserDoc user = new UserDoc();
        user.uid = "u1";
        user.username = "Habib.R";
        PublicProfileDoc pub = PublicProfileDoc.from(user);
        assertEquals("habib.r", pub.usernameLower);
        assertEquals("habib.r", pub.toMap().get("usernameLower"));
    }
}
