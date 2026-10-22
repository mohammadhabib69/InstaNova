package com.insta.nova.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.google.gson.JsonObject;
import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.StoryGroupDto;
import com.insta.nova.util.Resource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Behaviour of the seen-state flow:
 *   - 2xx inserts are surfaced as SUCCESS and trigger a tray refresh.
 *   - The expected duplicate-key reply (HTTP 409, Postgres 23505) is treated as
 *     "already seen" and still triggers a refresh; this is the repeat-view path.
 *   - Every other failure (auth, network, invalid uuid, unrelated DB error)
 *     is propagated as ERROR and does NOT trigger a refresh.
 *   - The signed-in viewer's id comes from the session, never from the caller.
 *   - When the session is unresolved (empty id) the repository surfaces an
 *     error without ever calling the API.
 */
public class StoryRepositorySeenStateTest {

    @Rule
    public final InstantTaskExecutorRule instantTask = new InstantTaskExecutorRule();

    private static final String STORY_ID = "5336c78a-efa2-44de-afc1-82cb3d40ea88";
    private static final String VIEWER_ID = "3c96a7cc-5c8b-4a79-9138-8c6c9491aed7";

    private final AtomicReference<String> viewerId = new AtomicReference<>(VIEWER_ID);
    private final AtomicInteger storyGroupsCalls = new AtomicInteger();
    private final List<Map<String, Object>> recordedBodies =
            Collections.synchronizedList(new ArrayList<>());
    private final List<Callback<Void>> pending =
            Collections.synchronizedList(new ArrayList<>());

    private StoryRepository repository;

    @Before
    public void setUp() {
        repository = new StoryRepository(fakeApi(), viewerId::get);
    }

    /* ------------------------------------------------------------------ */
    /* Fakes                                                               */
    /* ------------------------------------------------------------------ */

    private RestApi fakeApi() {
        return (RestApi) Proxy.newProxyInstance(
                RestApi.class.getClassLoader(),
                new Class<?>[]{RestApi.class},
                (proxy, method, args) -> {
                    if ("recordStoryView".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> body = (Map<String, Object>) args[0];
                        recordedBodies.add(body);
                        return fakeCall();
                    }
                    if ("getStoryGroups".equals(method.getName())) {
                        storyGroupsCalls.incrementAndGet();
                        // Return an empty list; markStorySeen only consults this
                        // to push a refreshed tray into the LiveData.
                        return fakeGroupCall();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private Call<Void> fakeCall() {
        return (Call<Void>) Proxy.newProxyInstance(
                Call.class.getClassLoader(),
                new Class<?>[]{Call.class},
                (proxy, method, args) -> {
                    if ("enqueue".equals(method.getName())) {
                        pending.add((Callback<Void>) args[0]);
                        return null;
                    }
                    if ("toString".equals(method.getName())) return "FakeStoryViewCall";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private Call<List<StoryGroupDto>> fakeGroupCall() {
        return (Call<List<StoryGroupDto>>) Proxy.newProxyInstance(
                Call.class.getClassLoader(),
                new Class<?>[]{Call.class},
                (proxy, method, args) -> {
                    if ("enqueue".equals(method.getName())) {
                        Callback<List<StoryGroupDto>> cb = (Callback<List<StoryGroupDto>>) args[0];
                        cb.onResponse(null, Response.success(Collections.emptyList()));
                        return null;
                    }
                    return null;
                });
    }

    private void completeSuccess() {
        pending.get(pending.size() - 1).onResponse(null,
                Response.success(null));
    }

    private void completeError(int http, String code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        ResponseBody body = ResponseBody.create(
                MediaType.parse("application/json"), error.toString());
        pending.get(pending.size() - 1).onResponse(null,
                Response.error(http, body));
    }

    private void completeNetwork() {
        pending.get(pending.size() - 1).onFailure(null, new IOException("boom"));
    }

    /* ------------------------------------------------------------------ */
    /* 1. First-time view: 201 → SUCCESS, refresh triggered               */
    /* ------------------------------------------------------------------ */

    @Test
    public void firstTimeInsertEmitsSuccessAndRefreshesTray() {
        storyGroupsCalls.set(0);
        repository.markStorySeen(STORY_ID);
        assertEquals(Resource.Status.LOADING, repository.getSeenState().getValue().status);

        completeSuccess();

        Resource<Boolean> last = repository.getSeenState().getValue();
        assertEquals(Resource.Status.SUCCESS, last.status);
        assertEquals(Boolean.TRUE, last.data);
        assertEquals("a confirmed insert must refresh the tray once",
                1, storyGroupsCalls.get());
        assertEquals("story_id is the row being marked",
                STORY_ID, recordedBodies.get(0).get("story_id"));
        assertEquals("viewer_id comes from the session, never the caller",
                VIEWER_ID, recordedBodies.get(0).get("viewer_id"));
    }

    /* ------------------------------------------------------------------ */
    /* 2. Repeated view: 409 + 23505 → SUCCESS, refresh triggered          */
    /* ------------------------------------------------------------------ */

    @Test
    public void duplicateKeyErrorIsTreatedAsAlreadySeen() {
        storyGroupsCalls.set(0);
        repository.markStorySeen(STORY_ID);
        completeError(409, "23505",
                "duplicate key value violates unique constraint \"story_views_pkey\"");

        Resource<Boolean> last = repository.getSeenState().getValue();
        assertEquals("23505 must surface as SUCCESS so the tray shows the row is on record",
                Resource.Status.SUCCESS, last.status);
        assertEquals(Boolean.TRUE, last.data);
        assertNull("no failure copy for the expected duplicate", last.message);
        assertEquals("refresh so v_stories.seen_by_me is authoritative",
                1, storyGroupsCalls.get());
    }

    /* ------------------------------------------------------------------ */
    /* 3. Unrelated DB error: 400 + 22P02 → ERROR, no refresh             */
    /* ------------------------------------------------------------------ */

    @Test
    public void invalidUuidErrorIsPropagatedAndDoesNotRefresh() {
        storyGroupsCalls.set(0);
        repository.markStorySeen(STORY_ID);
        completeError(400, "22P02",
                "invalid input syntax for type uuid: \"\"");

        Resource<Boolean> last = repository.getSeenState().getValue();
        assertEquals("22P02 is unrelated to uniqueness and must not be swallowed",
                Resource.Status.ERROR, last.status);
        assertNotNull(last.message);
        assertTrue("message carries the http status so logs are searchable",
                last.message.contains("HTTP 400"));
        assertEquals("an unrelated error must NOT optimistically refresh the tray",
                0, storyGroupsCalls.get());
    }

    /* ------------------------------------------------------------------ */
    /* 4. Auth error: 401 → ERROR                                          */
    /* ------------------------------------------------------------------ */

    @Test
    public void authErrorIsPropagated() {
        repository.markStorySeen(STORY_ID);
        completeError(401, "PGRST303", "JWT expired");

        Resource<Boolean> last = repository.getSeenState().getValue();
        assertEquals(Resource.Status.ERROR, last.status);
        assertEquals("auth failures must not refresh the tray",
                0, storyGroupsCalls.get());
    }

    /* ------------------------------------------------------------------ */
    /* 5. Network failure                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    public void networkFailureIsPropagated() {
        repository.markStorySeen(STORY_ID);
        completeNetwork();

        Resource<Boolean> last = repository.getSeenState().getValue();
        assertEquals(Resource.Status.ERROR, last.status);
        assertEquals("network failures must not refresh the tray",
                0, storyGroupsCalls.get());
    }

    /* ------------------------------------------------------------------ */
    /* 6. Missing viewer id → ERROR, no API call                           */
    /* ------------------------------------------------------------------ */

    @Test
    public void emptyViewerIdShortCircuitsWithoutAnApiCall() {
        viewerId.set("");
        repository.markStorySeen(STORY_ID);

        Resource<Boolean> last = repository.getSeenState().getValue();
        assertEquals("a blank viewer id must surface as an error, not a no-op",
                Resource.Status.ERROR, last.status);
        assertTrue(last.message.contains("Not authenticated"));
        assertEquals("must not enqueue when the identity is unresolved",
                0, recordedBodies.size());
    }

    @Test
    public void nullStoryIdIsRejectedWithoutAnApiCall() {
        repository.markStorySeen((String) null);

        Resource<Boolean> last = repository.getSeenState().getValue();
        assertEquals(Resource.Status.ERROR, last.status);
        assertEquals(0, recordedBodies.size());
    }

    /* ------------------------------------------------------------------ */
    /* 7. shouldRecordView contract                                        */
    /* ------------------------------------------------------------------ */

    @Test
    public void ownerIsNeverRecordedAsAViewer() {
        assertFalse("the owner's own story must not be recorded as seen",
                StoryRepository.shouldRecordView(VIEWER_ID, VIEWER_ID));
        assertTrue("someone else's story must be recorded",
                StoryRepository.shouldRecordView(VIEWER_ID, "someone-else"));
    }

    @Test
    public void blankIdsDoNotRecord() {
        assertFalse(StoryRepository.shouldRecordView("", VIEWER_ID));
        assertFalse(StoryRepository.shouldRecordView(VIEWER_ID, ""));
        assertFalse(StoryRepository.shouldRecordView("", ""));
    }
}