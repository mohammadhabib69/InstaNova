package com.insta.nova.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.insta.nova.data.entity.NetUsage;

import java.util.List;

@Dao
public interface NetUsageDao {

    @Insert
    long insert(NetUsage usage);

    @Query("SELECT * FROM net_usage WHERE day >= :fromDay ORDER BY day ASC, created_at ASC")
    List<NetUsage> since(String fromDay);

    @Query("SELECT * FROM net_usage WHERE day >= :fromDay ORDER BY day ASC, created_at ASC")
    LiveData<List<NetUsage>> observeSince(String fromDay);

    @Query("SELECT COALESCE(SUM(bytes),0) FROM net_usage WHERE day = :day")
    long bytesForDay(String day);

    @Query("SELECT COALESCE(SUM(est_full_bytes),0) FROM net_usage WHERE day = :day")
    long fullBytesForDay(String day);

    @Query("SELECT COALESCE(SUM(bytes),0) FROM net_usage WHERE day >= :fromDay")
    long bytesSince(String fromDay);

    @Query("SELECT COALESCE(SUM(est_full_bytes),0) FROM net_usage WHERE day >= :fromDay")
    long fullBytesSince(String fromDay);

    @Query("DELETE FROM net_usage WHERE day < :beforeDay")
    void deleteOlderThan(String beforeDay);

    @Query("DELETE FROM net_usage")
    void clear();
}
