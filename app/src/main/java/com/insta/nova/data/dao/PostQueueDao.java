package com.insta.nova.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.insta.nova.data.entity.QueuedPost;

import java.util.List;

@Dao
public interface PostQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long enqueue(QueuedPost post);

    @Query("SELECT * FROM queued_posts ORDER BY createdAt ASC")
    LiveData<List<QueuedPost>> observeAll();

    @Query("SELECT * FROM queued_posts WHERE status != 'uploading' ORDER BY createdAt ASC LIMIT 1")
    QueuedPost next();

    @Query("SELECT * FROM queued_posts WHERE id = :id LIMIT 1")
    QueuedPost byId(String id);

    @Query("UPDATE queued_posts SET status = :status, attempts = attempts + 1, lastError = :error WHERE id = :id")
    void mark(String id, String status, String error);

    @Query("DELETE FROM queued_posts WHERE id = :id")
    void delete(String id);

    @Query("UPDATE queued_posts SET status = 'queued' WHERE status = 'uploading'")
    void resetStuck();

    @Query("SELECT COUNT(*) FROM queued_posts")
    int count();
}
