package com.insta.nova.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One measured network response. {@code day} is an Asia/Dhaka calendar day. */
@Entity(tableName = "net_usage", indices = {@Index("day"), @Index("category")})
public class NetUsage {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "day")
    public String day;

    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "bytes")
    public long bytes;

    @ColumnInfo(name = "saver_active")
    public boolean saverActive;

    /** Equivalent bytes at full quality; for saver images = bytes / scale^2. */
    @ColumnInfo(name = "est_full_bytes")
    public long estFullBytes;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
