package com.insta.nova.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.insta.nova.data.remote.api.RestApi;
import com.insta.nova.data.remote.dto.UnreadCountsDto;
import com.insta.nova.util.Resource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Behaviour of the shared unread-count request: one in-flight call, a 30 s
 * success-only freshness window, and an explicit force path.
 *
 * <p>Uses a proxy {@link RestApi} and a controllable clock, so no network,
 * no Android runtime and no architectural change to the repository.
 */
public class ChatRepositoryUnreadTest {

    @Rule
    public final InstantTaskExecutorRule instantTask = new InstantTaskExecutorRule();

    private final AtomicLong now = new AtomicLong(1_000L);
    private final AtomicInteger enqueued = new AtomicInteger();
    private final List<Callback<UnreadCountsDto>> pending =
            Collections.synchronizedList(new ArrayList<>());

    private ChatRepository repository;

    @Before
    public void setUp() {
        repository = new ChatRepository(fakeApi(), null, now::get);
    }

    /* ------------------------------------------------------------------ */
    /* Fakes                                                               */
    /* ------------------------------------------------------------------ */

    private RestApi fakeApi() {
        return (RestApi) Proxy.newProxyInstance(
                RestApi.class.getClassLoader(),
                new Class<?>[]{RestApi.class},
                (proxy, method, args) -> {
                    if ("getUnreadCounts".equals(method.getName())) {
                        enqueued.incrementAndGet();
                        return fakeCall();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private Call<UnreadCountsDto> fakeCall() {
        return (Call<UnreadCountsDto>) Proxy.newProxyInstance(
                Call.class.getClassLoader(),
                new Class<?>[]{Call.class},
                (proxy, method, args) -> {
                    if ("enqueue".equals(method.getName())) {
                        pending.add((Callback<UnreadCountsDto>) args[0]);
                        return null;
                    }
                    if ("toString".equals(method.getName())) return "FakeCall";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                });
    }

    private void completeSuccess(int notifications, int messages) {
        pending.get(pending.size() - 1)
                .onResponse(null, Response.success(new UnreadCountsDto(notifications, messages)));
    }

    private void completeFailure() {
        pending.get(pending.size() - 1).onFailure(null, new IOException("boom"));
    }

    /* ------------------------------------------------------------------ */
    /* 1. Concurrent callers share one request                             */
    /* ------------------------------------------------------------------ */

    @Test
    public void secondCallWhileInFlightDoesNotEnqueue() {
        assertTrue(repository.refreshUnreadCounts(false));
        assertFalse(repository.refreshUnreadCounts(false));
        assertEquals(1, enqueued.get());
    }

    @Test
    public void concurrentThreadsEnqueueExactlyOnce() throws Exception {
        final int threads = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger started = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (repository.refreshUnreadCounts(false)) started.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertEquals("only one request may start", 1, enqueued.get());
        assertEquals("only one caller may report having started it", 1, started.get());
    }

    @Test
    public void badgePollAndHomeResumeShareOneRequest() {
        // The original duplicate: MainActivity's poll, then Home's onResume 27 ms later.
        now.set(1_000L);
        assertTrue(repository.refreshUnreadCounts(false));
        now.set(1_027L);
        assertFalse("Home must join the poll's request", repository.refreshUnreadCounts(false));
        assertEquals(1, enqueued.get());
    }

    /* ------------------------------------------------------------------ */
    /* 2-3. Freshness window                                               */
    /* ------------------------------------------------------------------ */

    @Test
    public void ttlIsShorterThanTheBadgePollInterval() {
        // If the TTL matched the 30 s poll, a tick landing just inside the window
        // would be skipped and badges would refresh only every other interval.
        repository.refreshUnreadCounts(false);
        completeSuccess(0, 0);

        now.set(1_000L + com.insta.nova.util.Constants.POLLING_BADGES_MS);
        assertTrue("a poll tick must never be starved by the TTL",
                repository.refreshUnreadCounts(false));
    }

    @Test
    public void refreshInsideTtlDoesNotEnqueue() {
        repository.refreshUnreadCounts(false);
        completeSuccess(1, 2);

        now.set(1_000L + ChatRepository.UNREAD_TTL_MS - 1);
        assertFalse(repository.refreshUnreadCounts(false));
        assertEquals(1, enqueued.get());
    }

    @Test
    public void refreshAfterTtlEnqueuesAgain() {
        repository.refreshUnreadCounts(false);
        completeSuccess(1, 2);

        now.set(1_000L + ChatRepository.UNREAD_TTL_MS + 1);
        assertTrue(repository.refreshUnreadCounts(false));
        assertEquals(2, enqueued.get());
    }

    /* ------------------------------------------------------------------ */
    /* 4. Failures never populate the cache                                */
    /* ------------------------------------------------------------------ */

    @Test
    public void failureDoesNotStartFreshnessWindow() {
        repository.refreshUnreadCounts(false);
        completeFailure();

        now.set(1_500L); // well inside any TTL measured from the failure
        assertTrue("a failed call must be retried immediately", repository.refreshUnreadCounts(false));
        assertEquals(2, enqueued.get());
    }

    @Test
    public void failureEmitsErrorAndIsNotCached() {
        repository.refreshUnreadCounts(false);
        completeFailure();

        assertEquals(Resource.Status.ERROR, repository.unreadCounts().getValue().status);
    }

    /* ------------------------------------------------------------------ */
    /* 5. Force refresh                                                    */
    /* ------------------------------------------------------------------ */

    @Test
    public void forceRefreshBypassesFreshnessWindow() {
        repository.refreshUnreadCounts(false);
        completeSuccess(0, 0);

        now.set(1_100L); // still fresh
        assertFalse(repository.refreshUnreadCounts(false));
        assertTrue("force must bypass the TTL", repository.refreshUnreadCounts(true));
        assertEquals(2, enqueued.get());
    }

    @Test
    public void forceRefreshStillSharesAnInFlightRequest() {
        repository.refreshUnreadCounts(true);
        assertFalse(repository.refreshUnreadCounts(true));
        assertEquals(1, enqueued.get());
    }

    /* ------------------------------------------------------------------ */
    /* 6. Stale / duplicate callbacks cannot corrupt state                  */
    /* ------------------------------------------------------------------ */

    @Test
    public void lateFailureAfterSuccessDoesNotClearTheCache() {
        repository.refreshUnreadCounts(false);
        completeSuccess(3, 4);

        // Same callback firing again: a superseded or duplicated signal.
        pending.get(0).onFailure(null, new IOException("late"));

        now.set(1_500L);
        assertFalse("cache must survive a stale callback", repository.refreshUnreadCounts(false));
        assertEquals(1, enqueued.get());
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle / observer identity                                       */
    /* ------------------------------------------------------------------ */

    @Test
    public void unreadCountsReturnsTheSameSourceForEveryObserver() {
        assertSame("observers must not get per-call LiveData instances",
                repository.unreadCounts(), repository.unreadCounts());
    }

    @Test
    public void observingDoesNotTriggerARequest() {
        repository.unreadCounts().observeForever(resource -> { });
        assertEquals("attaching an observer must not fetch", 0, enqueued.get());
    }

    @Test
    public void firstRefreshEmitsLoadingThenSuccess() {
        repository.refreshUnreadCounts(false);
        assertEquals(Resource.Status.LOADING, repository.unreadCounts().getValue().status);

        completeSuccess(5, 6);
        Resource<UnreadCountsDto> last = repository.unreadCounts().getValue();
        assertEquals(Resource.Status.SUCCESS, last.status);
        assertEquals(5, last.data.getNotifications());
        assertEquals(6, last.data.getDirectMessages());
    }
}
