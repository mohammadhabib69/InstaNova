package com.insta.nova.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.insta.nova.data.dao.FeedCacheDao;
import com.insta.nova.data.dao.NetUsageDao;
import com.insta.nova.data.dao.PostQueueDao;
import com.insta.nova.data.entity.CachedFeed;
import com.insta.nova.data.entity.NetUsage;
import com.insta.nova.data.entity.QueuedPost;

@Database(entities = {NetUsage.class, CachedFeed.class, QueuedPost.class}, version = 1, exportSchema = false)
public abstract class NovaDb extends RoomDatabase {

    private static volatile NovaDb instance;

    public abstract NetUsageDao netUsageDao();

    public abstract FeedCacheDao feedCacheDao();

    public abstract PostQueueDao postQueueDao();

    public static NovaDb get(Context context) {
        if (instance == null) {
            synchronized (NovaDb.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    NovaDb.class, "nova.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
