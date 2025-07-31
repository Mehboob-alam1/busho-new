package com.mehboob.bushorat.models;

public class Game {
    public String name;
    public String url;
    public int imageResId;

    public Game(String name, String url, int imageResId) {
        this.name = name;
        this.url = url;
        this.imageResId = imageResId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getImageResId() {
        return imageResId;
    }

    public void setImageResId(int imageResId) {
        this.imageResId = imageResId;
    }
}

