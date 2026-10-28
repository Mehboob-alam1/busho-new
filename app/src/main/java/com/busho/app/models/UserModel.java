package com.busho.app.models;

public class UserModel {
    private String name;
    private String gmail;
    private String password;
    private String whatsappNumber;
    private String address;
    private String userId;

    private long coins;


    public UserModel() {
        // Required for Firebase
    }

    public UserModel(String name, String gmail, String password, String whatsappNumber, String address, String userId,long coins) {
        this.name = name;
        this.gmail = gmail;
        this.password = password;
        this.whatsappNumber = whatsappNumber;
        this.userId=userId;
        this.address = address;
        this.coins = coins;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // Getters and setters
    public String getName() { return name; }
    public String getGmail() { return gmail; }
    public String getPassword() { return password; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getAddress() { return address; }

    public void setName(String name) { this.name = name; }
    public void setGmail(String gmail) { this.gmail = gmail; }
    public void setPassword(String password) { this.password = password; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }
    public void setAddress(String address) { this.address = address; }
}
