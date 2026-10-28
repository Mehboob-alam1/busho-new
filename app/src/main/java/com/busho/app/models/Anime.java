package com.busho.app.models;


public class Anime {

    private String website_link;
    private String website_logo;
    public Anime() {
    }

    public Anime (String website_link, String website_logo) {
        this.website_link = website_link;
        this.website_logo = website_logo;

    }

    public String getWebsite_link() {
        return website_link;
    }

    public String getWebsite_logo() {
        return website_logo;
    }

    public void setWebsite_link(String website_link) {
        this.website_link = website_link;
    }

    public void setWebsite_logo(String website_logo) { this.website_logo = website_logo; }

}