package com.insta.nova.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A post captured while offline; the worker replays the existing upload pipeline. */
@Entity(tableName = "queued_posts")
public class QueuedPost {

    @NonNull
    @PrimaryKey
    public String id;

    public String caption;

    public String location;

    /** Newline-separated absolute paths inside app storage. */
    public String imagePaths;

    /** queued | uploading | failed */
    public String status;

    public int attempts;

    public long createdAt;

    public String lastError;

    public QueuedPost() {
    }
}
