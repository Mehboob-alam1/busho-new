package com.busho.app.models;

public class VideoModel {
    private String id;
    private String title;
    private String url;
    private String uploadedBy;
    private long uploadedAt;
    private long timestamp;
    private String thumbnailUrl;
    private String videoUrl;

    public VideoModel() {
        // Required empty constructor
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getThumbnailUrl() {
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            return thumbnailUrl;
        }
        return null;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getVideoUrl() {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            return videoUrl;
        }
        return url;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
}
