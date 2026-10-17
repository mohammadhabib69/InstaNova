package com.insta.nova.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain model representing a profile story highlight album.
 */
public class Highlight implements Serializable {
    private String id;
    private String userId;
    private String title;
    private String coverUrl;
    private String createdAt;
    private List<HighlightItem> items = new ArrayList<>();

    public Highlight() {}

    public Highlight(String id, String userId, String title, String coverUrl) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.coverUrl = coverUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<HighlightItem> getItems() { return items; }
    public void setItems(List<HighlightItem> items) { this.items = items != null ? items : new ArrayList<>(); }
}
