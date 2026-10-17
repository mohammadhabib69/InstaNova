package com.insta.nova.data.model;

import java.io.Serializable;

/**
 * Domain model representing an individual media item within a story highlight.
 */
public class HighlightItem implements Serializable {
    private String id;
    private String highlightId;
    private String mediaUrl;
    private String createdAt;

    public HighlightItem() {}

    public HighlightItem(String id, String highlightId, String mediaUrl) {
        this.id = id;
        this.highlightId = highlightId;
        this.mediaUrl = mediaUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHighlightId() { return highlightId; }
    public void setHighlightId(String highlightId) { this.highlightId = highlightId; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
