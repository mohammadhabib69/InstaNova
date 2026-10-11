package com.insta.clone.data.model;

import java.io.Serializable;

/**
 * Domain model representing an individual image or video media asset within a post.
 */
public class PostMedia implements Serializable {
    private String id;
    private String postId;
    private String url;
    private int position;

    public PostMedia() {}

    public PostMedia(String id, String postId, String url, int position) {
        this.id = id;
        this.postId = postId;
        this.url = url;
        this.position = position;
    }

    public PostMedia(String url, int position) {
        this.url = url;
        this.position = position;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
