package com.insta.nova.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Last successful Home payload (posts + story groups) for offline display. */
@Entity(tableName = "cached_feed")
public class CachedFeed {

    @NonNull
    @PrimaryKey
    public String id;

    public String payload;

    public long savedAt;

    public CachedFeed() {
    }

    public CachedFeed(String id, String payload, long savedAt) {
        this.id = id;
        this.payload = payload;
        this.savedAt = savedAt;
    }
}
