package com.insta.nova.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Shared unread count for the Nova tab dot, the hub strip and the alerts screen. */
public final class NovaBadgeStore {

    private static final NovaBadgeStore INSTANCE = new NovaBadgeStore();
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);

    private NovaBadgeStore() {
    }

    public static NovaBadgeStore get() {
        return INSTANCE;
    }

    public LiveData<Integer> unread() {
        return unread;
    }

    public int value() {
        Integer value = unread.getValue();
        return value == null ? 0 : value;
    }

    public void set(int count) {
        unread.postValue(Math.max(0, count));
    }

    public void decrement() {
        unread.postValue(Math.max(0, value() - 1));
    }
}
