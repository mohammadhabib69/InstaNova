package com.insta.nova.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.insta.nova.data.entity.CachedFeed;

@Dao
public interface FeedCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(CachedFeed feed);

    @Query("SELECT * FROM cached_feed WHERE id = :id LIMIT 1")
    CachedFeed get(String id);

    @Query("DELETE FROM cached_feed")
    void clear();
}
