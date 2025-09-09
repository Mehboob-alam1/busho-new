package com.mehboob.bushorat.helpers;

import android.webkit.JavascriptInterface;

import com.mehboob.bushorat.activities.CallActivity;


public class InterfaceJava {

    CallActivity callActivity;

    public InterfaceJava(CallActivity callActivity) {
        this.callActivity = callActivity;
    }

    @JavascriptInterface
    public void onPeerConnected(){
        callActivity.onPeerConnected();
    }

    @JavascriptInterface
    public void onConnectionStatus(String status){
        callActivity.onConnectionStatus(status);
    }

    @JavascriptInterface
    public void onError(String error){
        callActivity.onError(error);
    }

    @JavascriptInterface
    public void onQualityUpdate(String audioQuality, String videoQuality, String connectionType){
        callActivity.onQualityUpdate(audioQuality, videoQuality, connectionType);
    }

}
