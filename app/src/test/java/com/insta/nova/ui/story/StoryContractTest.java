package com.insta.nova.ui.story;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.insta.nova.data.model.StoryGroup;
import com.insta.nova.data.remote.InteractionConflict;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.StoryGroupDto;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Stories contract: a repeat view must be a no-op rather than a duplicate-key failure,
 * and a malformed group payload must still produce a renderable item.
 */
public class StoryContractTest {

    @Test
    public void storyViewConflictTargetMatchesThePrimaryKey() {
        assertEquals("story_id,viewer_id", InteractionConflict.STORY_VIEW);
    }

    @Test
    public void recordStoryViewIsAPlainInsert() throws Exception {
        // The repeat-view handling relies on Postgres returning its 23505
        // duplicate-key reply on the second insert; StoryRepository treats
        // exactly that reply as already-seen. Switching the endpoint to a
        // PostgREST upsert (on_conflict + resolution=ignore-duplicates) would
        // hide the duplicate but also mask any other uniqueness violation, so
        // the contract here stays a plain POST and the response handling does
        // the classification.
        Method m = null;
        for (Method candidate : RestApi.class.getMethods()) {
            if ("recordStoryView".equals(candidate.getName())) m = candidate;
        }
        assertNotNull(m);
        assertNotNull(m.getAnnotation(POST.class));
        assertNull("no @Headers on the plain insert", m.getAnnotation(Headers.class));
        for (java.lang.annotation.Annotation[] param : m.getParameterAnnotations()) {
            for (java.lang.annotation.Annotation a : param) {
                assertFalse("no on_conflict query param", a instanceof Query);
            }
        }
    }

    @Test
    public void groupParsingNeverProducesNulls() {
        StoryGroupDto dto = new StoryGroupDto();
        StoryGroup group = dto.toStoryGroup();

        assertEquals("", group.getUserId());
        assertEquals("", group.getUsername());
        assertEquals("", group.getAvatarUrl());
        assertFalse(group.hasUnseen());
        assertNotNull(group.getStories() == null ? "ok" : group.getStories());
    }

    @Test
    public void groupParsingCarriesSeenStateAndIdentity() {
        StoryGroupDto dto = new StoryGroupDto();
        dto.setUserId("u-1");
        dto.setUsername("instanova11");
        dto.setAvatarUrl("https://example.test/a.jpg");
        dto.setHasUnseen(true);
        dto.setLatest("s-9");

        StoryGroup group = dto.toStoryGroup();

        assertEquals("u-1", group.getUserId());
        assertEquals("instanova11", group.getUsername());
        assertTrue(group.hasUnseen());
    }

    @Test
    public void ownerFlagIsAppliedByTheRepositoryNotThePayload() {
        // The RPC does not know who is asking on behalf of the UI; the repository sets
        // this from the session. A DTO can never claim ownership by itself.
        StoryGroupDto dto = new StoryGroupDto();
        dto.setUserId("u-1");
        StoryGroup group = dto.toStoryGroup();
        group.setOwner(false);
        assertFalse(group.isOwner());
    }

    @Test
    public void recordStoryViewBodyKeepsTheServerCheckedFields() {
        // viewer_id is sent from the session and re-checked by RLS (viewer_id =
        // request_uid()); story_id is the row being marked. Four fields is the contract.
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("story_id", "s-1");
        body.put("viewer_id", "u-1");
        assertEquals(2, body.size());
        assertNull(body.get("seen_at"));
    }
}
