package com.mehboob.bushorat.models;

public class CallLogModel {
    private String phoneNumber;
    private String callType;
    private String callDate;
    private String callDuration;

    public CallLogModel(String phoneNumber, String callType, String callDate, String callDuration) {
        this.phoneNumber = phoneNumber;
        this.callType = callType;
        this.callDate = callDate;
        this.callDuration = callDuration;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getCallType() {
        return callType;
    }

    public String getCallDate() {
        return callDate;
    }

    public String getCallDuration() {
        return callDuration;
    }

    @Override
    public String toString() {
        return "CallLogModel{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", callType='" + callType + '\'' +
                ", callDate='" + callDate + '\'' +
                ", callDuration='" + callDuration + '\'' +
                '}';
    }
}
