package com.busho.app.models;

public class ChatMessage {
    public String id;
    public String sender;
    public String type;
    public String text;
    public String fileUrl;
    public String fileName;
    public String mimeType;
    public long timestamp;

    public boolean isFromUser() {
        return "user".equals(sender);
    }
}
