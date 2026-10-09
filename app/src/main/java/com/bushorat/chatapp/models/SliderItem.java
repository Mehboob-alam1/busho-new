package com.bushorat.chatapp.models;

public class SliderItem {
    private String imgUrl;
    private String url;

    public SliderItem() {
        // Default constructor required for Firebase
    }

    public SliderItem(String imgUrl, String url) {
        this.imgUrl = imgUrl;
        this.url = url;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

