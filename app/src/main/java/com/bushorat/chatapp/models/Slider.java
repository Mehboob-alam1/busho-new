package com.bushorat.chatapp.models;


public class Slider {
    private String imageUrl;
    private String imageLink;
    private String pushId;

    public Slider() {
    }

    public Slider(String imageUrl, String imageLink, String pushId) {
        this.imageUrl = imageUrl;
        this.imageLink = imageLink;
        this.pushId = pushId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPushId() {
        return pushId;
    }

    public void setPushId(String pushId) {
        this.pushId = pushId;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageLink() {
        return imageLink;
    }

    public void setImageLink(String imageLink) {
        this.imageLink = imageLink;
    }
}