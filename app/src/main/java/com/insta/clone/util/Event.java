package com.insta.clone.util;

/**
 * Single-event wrapper for LiveData (e.g., navigation events, toast triggers).
 *
 * @param <T> event payload
 */
public class Event<T> {

    private final T content;
    private boolean hasBeenHandled = false;

    public Event(T content) {
        this.content = content;
    }

    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return content;
        }
    }

    public T peekContent() {
        return content;
    }

    public boolean hasBeenHandled() {
        return hasBeenHandled;
    }
}
