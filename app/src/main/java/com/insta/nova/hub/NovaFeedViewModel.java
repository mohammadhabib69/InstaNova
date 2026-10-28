package com.insta.nova.hub;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.insta.nova.data.NovaDtos;
import com.insta.nova.data.local.SessionManager;
import com.insta.nova.data.repository.NovaPostRepository;
import com.insta.nova.util.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * State for the Nova hub feed.
 *
 * <p>The feed reads {@code nova_posts_public} and nothing else, so an
 * Instagram post can never appear here and a Nova post can never appear on
 * Instagram.
 *
 * <p>Three things shape the list: the chip rail (All, Photos, Reels, Saved,
 * Mine), the sort (Hot, New, Top) and the filter sheet (content type, hide
 * anonymous, hide expiring soon). Sorting is done by the server, because the
 * Hot ranking depends on the age of every row.
 */
public class NovaFeedViewModel extends AndroidViewModel {

    private static final String TAG = "NovaFeedViewModel";
    private static final int PAGE_SIZE = 100;

    /** A post is "expiring soon" when it has less than this left. */
    private static final long EXPIRING_SOON_MS = 60L * 60L * 1000L;

    /** Which slice of the feed the chip rail is showing. */
    public enum Filter { ALL, PHOTOS, REELS, SAVED, MINE }

    /** How the feed is ordered. */
    public enum Sort { HOT, NEW, TOP }

    /** The content type the filter sheet asks for. */
    public static final String TYPE_ALL = "all";
    public static final String TYPE_PHOTO = "photo";
    public static final String TYPE_REEL = "reel";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_POLL = "poll";

    private final NovaPostRepository repository;
    private final SessionManager sessionManager;

    private final MutableLiveData<List<NovaDtos.NovaPost>> postsLive =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> initialLoadingLive = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> refreshingLive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMoreLive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMoreLive = new MutableLiveData<>(false);

    /**
     * How many raw rows have been pulled for the current filter and sort.
     *
     * <p>This is not the same as the number of cards on screen: the feed drops
     * expired rows and anything the filter sheet hides, so the next page has to
     * be asked for by row offset or those rows would be requested twice.
     */
    private int rawLoaded;

    /**
     * The paging guard. This is a plain field rather than the LiveData because
     * a scroll event can arrive between the response and the LiveData update,
     * which asked for the same page a second time.
     */
    private boolean hasMoreFlag;
    private final MutableLiveData<String> errorLive = new MutableLiveData<>();
    private final MutableLiveData<Filter> filterLive = new MutableLiveData<>(Filter.ALL);
    private final MutableLiveData<Sort> sortLive = new MutableLiveData<>(Sort.HOT);
    private final MutableLiveData<String> contentTypeLive = new MutableLiveData<>(TYPE_ALL);
    private final MutableLiveData<Boolean> hideAnonymousLive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hideExpiringSoonLive = new MutableLiveData<>(false);

    private final List<RequestObserver<?>> activeObservers = new ArrayList<>();

    private boolean started;

    public NovaFeedViewModel(@NonNull Application application) {
        super(application);
        this.repository = new NovaPostRepository(application);
        this.sessionManager = SessionManager.getInstance(application);
    }

    /* ------------------------------------------------------------------ */
    /* Exposed state                                                       */
    /* ------------------------------------------------------------------ */

    public LiveData<List<NovaDtos.NovaPost>> posts() { return postsLive; }
    public LiveData<Boolean> initialLoading() { return initialLoadingLive; }
    public LiveData<Boolean> refreshing() { return refreshingLive; }

    public LiveData<Boolean> loadingMore() { return loadingMoreLive; }

    public LiveData<Boolean> hasMore() { return hasMoreLive; }
    public LiveData<String> error() { return errorLive; }
    public LiveData<Filter> filter() { return filterLive; }
    public LiveData<Sort> sort() { return sortLive; }
    public LiveData<String> contentType() { return contentTypeLive; }
    public LiveData<Boolean> hideAnonymous() { return hideAnonymousLive; }
    public LiveData<Boolean> hideExpiringSoon() { return hideExpiringSoonLive; }

    public Filter currentFilter() {
        Filter value = filterLive.getValue();
        return value == null ? Filter.ALL : value;
    }

    public Sort currentSort() {
        Sort value = sortLive.getValue();
        return value == null ? Sort.HOT : value;
    }

    public String currentContentType() {
        String value = contentTypeLive.getValue();
        return value == null || value.trim().isEmpty() ? TYPE_ALL : value;
    }

    public boolean currentHideAnonymous() {
        return Boolean.TRUE.equals(hideAnonymousLive.getValue());
    }

    public boolean currentHideExpiringSoon() {
        return Boolean.TRUE.equals(hideExpiringSoonLive.getValue());
    }

    /* ------------------------------------------------------------------ */
    /* Inputs                                                              */
    /* ------------------------------------------------------------------ */

    public void loadInitial() {
        if (started) return;
        started = true;
        reload(true);
    }

    public void refresh() {
        if (Boolean.TRUE.equals(refreshingLive.getValue())) return;
        started = true;
        reload(false);
    }

    public void setFilter(Filter filter) {
        if (filter == null || filter == currentFilter()) return;
        filterLive.setValue(filter);
        // The chip and the filter sheet share the content type, so picking a
        // chip clears a type the sheet had set.
        if (filter == Filter.PHOTOS) {
            contentTypeLive.setValue(TYPE_PHOTO);
        } else if (filter == Filter.REELS) {
            contentTypeLive.setValue(TYPE_REEL);
        } else if (filter == Filter.ALL) {
            contentTypeLive.setValue(TYPE_ALL);
        }
        reload(true);
    }

    public void setSort(Sort sort) {
        if (sort == null || sort == currentSort()) return;
        sortLive.setValue(sort);
        reload(false);
    }

    public void setContentType(String type) {
        if (type == null) type = TYPE_ALL;
        if (type.equals(currentContentType())) return;
        contentTypeLive.setValue(type);
        // Keep the chip rail in step with the sheet.
        if (TYPE_PHOTO.equals(type)) {
            filterLive.setValue(Filter.PHOTOS);
        } else if (TYPE_REEL.equals(type)) {
            filterLive.setValue(Filter.REELS);
        } else if (TYPE_ALL.equals(type)) {
            filterLive.setValue(Filter.ALL);
        }
        reload(true);
    }

    public void setHideAnonymous(boolean hide) {
        if (hide == currentHideAnonymous()) return;
        hideAnonymousLive.setValue(hide);
        reload(false);
    }

    public void setHideExpiringSoon(boolean hide) {
        if (hide == currentHideExpiringSoon()) return;
        hideExpiringSoonLive.setValue(hide);
        reload(false);
    }

    /** Applies a whole filter sheet at once. */
    public void applyFilters(Sort sort, String contentType, boolean hideAnonymous,
                              boolean hideExpiringSoon) {
        if (sort != null) sortLive.setValue(sort);
        if (contentType != null) contentTypeLive.setValue(contentType);
        hideAnonymousLive.setValue(hideAnonymous);
        hideExpiringSoonLive.setValue(hideExpiringSoon);
        reload(false);
    }

    /* ------------------------------------------------------------------ */
    /* Loading                                                             */
    /* ------------------------------------------------------------------ */

    private void reload(boolean showSpinner) {
        final int requestGeneration = ++generation;
        errorLive.setValue(null);
        if (showSpinner) initialLoadingLive.setValue(true);
        refreshingLive.setValue(true);

        final Filter filter = currentFilter();
        final String type = effectiveContentType(filter);
        final boolean savedOnly = filter == Filter.SAVED;
        final boolean mineOnly = filter == Filter.MINE;

        try {
            observeOnce(repository.feed(type, sortKey(currentSort()), savedOnly, mineOnly,
                    PAGE_SIZE, 0), resource -> {
                if (requestGeneration != generation || resource == null) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    List<NovaDtos.NovaPost> page = resource.data == null
                            ? new ArrayList<>() : resource.data;
                    rawLoaded = page.size();
                    hasMoreFlag = page.size() >= PAGE_SIZE;
                    hasMoreLive.postValue(hasMoreFlag);
                    postsLive.postValue(decorate(page));
                    errorLive.postValue(null);
                } else if (resource.status == Resource.Status.ERROR) {
                    rawLoaded = 0;
                    hasMoreFlag = false;
                    hasMoreLive.postValue(false);
                    postsLive.postValue(decorate(new ArrayList<>()));
                    errorLive.postValue(friendlyError(resource.message));
                }
                if (resource.status == Resource.Status.SUCCESS
                        || resource.status == Resource.Status.ERROR) {
                    initialLoadingLive.postValue(false);
                    refreshingLive.postValue(false);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to request Nova posts", e);
            initialLoadingLive.setValue(false);
            refreshingLive.setValue(false);
            errorLive.setValue("Could not load posts.");
        }
    }

    /**
     * Appends the next page to the feed.
     *
     * <p>Rows are merged by id, so a card that comes back twice (its rank or
     * score moved between pages) is only shown once. A failed page is not fatal:
     * what is already on screen stays, and paging stops until the next refresh.
     */
    public void loadMore() {
        if (Boolean.TRUE.equals(loadingMoreLive.getValue())) return;
        if (!hasMoreFlag) return;
        if (Boolean.TRUE.equals(refreshingLive.getValue())) return;

        final List<NovaDtos.NovaPost> current = postsLive.getValue();
        if (current == null || current.isEmpty()) return;

        final int requestGeneration = generation;
        final Filter filter = currentFilter();
        final String type = effectiveContentType(filter);
        final boolean savedOnly = filter == Filter.SAVED;
        final boolean mineOnly = filter == Filter.MINE;
        final int offset = rawLoaded;

        loadingMoreLive.setValue(true);
        try {
            observeOnce(repository.feed(type, sortKey(currentSort()), savedOnly, mineOnly,
                    PAGE_SIZE, offset), resource -> {
                if (requestGeneration != generation || resource == null) return;
                // The request emits LOADING first. Releasing the guard there
                // let the next scroll start a second fetch of the same page.
                if (resource.status == Resource.Status.LOADING) return;

                loadingMoreLive.postValue(false);

                if (resource.status != Resource.Status.SUCCESS) {
                    hasMoreFlag = false;
                    hasMoreLive.postValue(false);
                    return;
                }

                List<NovaDtos.NovaPost> page = resource.data == null
                        ? new ArrayList<>() : resource.data;
                rawLoaded = offset + page.size();
                hasMoreFlag = page.size() >= PAGE_SIZE;
                hasMoreLive.postValue(hasMoreFlag);

                Set<String> seen = new HashSet<>();
                for (NovaDtos.NovaPost post : current) {
                    if (post != null && post.id != null) seen.add(post.id);
                }
                List<NovaDtos.NovaPost> merged = new ArrayList<>(current);
                for (NovaDtos.NovaPost post : decorate(page)) {
                    if (post == null || post.id == null) continue;
                    if (seen.add(post.id)) merged.add(post);
                }
                postsLive.postValue(merged);
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to load the next Nova page", e);
            loadingMoreLive.setValue(false);
            hasMoreFlag = false;
            hasMoreLive.setValue(false);
        }
    }

    /**
     * The content type the server is asked for.
     *
     * <p>The sheet's type wins over the chip, except for the two chips that
     * are not content types at all (Saved and Mine).
     */
    private String effectiveContentType(Filter filter) {
        String sheetType = currentContentType();
        if (filter == Filter.PHOTOS) return TYPE_PHOTO;
        if (filter == Filter.REELS) return TYPE_REEL;
        if (filter == Filter.SAVED || filter == Filter.MINE) return TYPE_ALL;
        return sheetType;
    }

    private static String sortKey(Sort sort) {
        if (sort == Sort.TOP) return "top";
        if (sort == Sort.NEW) return "new";
        return "hot";
    }

    /** Drops the rows the filters exclude. */
    private List<NovaDtos.NovaPost> decorate(List<NovaDtos.NovaPost> source) {
        List<NovaDtos.NovaPost> alive = NovaPostRepository.withoutExpired(source);
        boolean hideAnonymous = currentHideAnonymous();
        boolean hideExpiring = currentHideExpiringSoon();
        long now = System.currentTimeMillis();

        List<NovaDtos.NovaPost> out = new ArrayList<>();
        for (NovaDtos.NovaPost post : alive) {
            if (post == null) continue;
            if (hideAnonymous && post.isAnonymous) continue;
            if (hideExpiring && post.hasExpiry()
                    && (post.expiresAtMillis() - now) <= EXPIRING_SOON_MS) {
                continue;
            }
            out.add(post);
        }
        return out;
    }

    /** Re-applies the client side filters to the rows already in hand. */
    public void applyClientFilters() {
        List<NovaDtos.NovaPost> current = postsLive.getValue();
        postsLive.setValue(decorate(current == null ? new ArrayList<>() : current));
    }

    /** Replaces one row in place after a vote, a save or a share. */
    public void updatePost(NovaDtos.NovaPost updated) {
        if (updated == null || updated.id == null) return;
        List<NovaDtos.NovaPost> current = postsLive.getValue();
        if (current == null) return;
        List<NovaDtos.NovaPost> copy = new ArrayList<>();
        boolean found = false;
        for (NovaDtos.NovaPost post : current) {
            if (post != null && post.id.equals(updated.id)) {
                copy.add(updated);
                found = true;
            } else {
                copy.add(post);
            }
        }
        if (found) postsLive.setValue(copy);
    }

    private String friendlyError(String message) {
        if (message == null || message.trim().isEmpty()) return "Could not load posts.";
        if (message.contains("does not exist") || message.contains("42703")
                || message.contains("42P01")) {
            return "Nova is not set up on the server yet. Run the Nova migration, then retry.";
        }
        return message;
    }

    /* ------------------------------------------------------------------ */
    /* Observer bookkeeping                                                */
    /* ------------------------------------------------------------------ */

    private int generation;

    private <T> void observeOnce(LiveData<Resource<T>> source,
                                  androidx.lifecycle.Observer<Resource<T>> callback) {
        if (source == null) return;
        RequestObserver<T> observer = new RequestObserver<>(source, callback);
        activeObservers.add(observer);
        source.observeForever(observer);
    }

    @Override
    protected void onCleared() {
        for (RequestObserver<?> observer : new ArrayList<>(activeObservers)) {
            observer.dispose();
        }
        activeObservers.clear();
        super.onCleared();
    }

    private final class RequestObserver<T> implements androidx.lifecycle.Observer<Resource<T>> {
        private final LiveData<Resource<T>> source;
        private final androidx.lifecycle.Observer<Resource<T>> callback;
        private boolean disposed;

        RequestObserver(LiveData<Resource<T>> source,
                        androidx.lifecycle.Observer<Resource<T>> callback) {
            this.source = source;
            this.callback = callback;
        }

        @Override
        public void onChanged(Resource<T> resource) {
            if (disposed || resource == null) return;
            callback.onChanged(resource);
            if (resource.status == Resource.Status.SUCCESS || resource.status == Resource.Status.ERROR) {
                dispose();
            }
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            source.removeObserver(this);
            activeObservers.remove(this);
        }
    }
}
