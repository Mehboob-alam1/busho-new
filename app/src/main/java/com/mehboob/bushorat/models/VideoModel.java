package com.mehboob.bushorat.models;

public class VideoModel {
    private String thumbnailUrl;
    private String videoUrl;

    // Empty constructor for Firebase
    public VideoModel() {}

    // Getters and setters
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
}
