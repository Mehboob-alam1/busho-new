package com.bushorat.chatapp.helpers;

import android.webkit.JavascriptInterface;

import com.bushorat.chatapp.activities.CallActivity;

public class InterfaceJava {

    CallActivity callActivity;

    public InterfaceJava(CallActivity callActivity) {
        this.callActivity = callActivity;
    }

    @JavascriptInterface
    public void onPeerConnected() {
        callActivity.onPeerConnected();
    }

    @JavascriptInterface
    public void onPeerError(String message) {
        callActivity.onPeerError(message);
    }

    @JavascriptInterface
    public void onRemoteConnected() {
        callActivity.onRemoteConnected();
    }
}
