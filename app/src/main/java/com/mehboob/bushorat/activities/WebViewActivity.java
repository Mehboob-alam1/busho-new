package com.mehboob.bushorat.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.mehboob.bushorat.R;

import maes.tech.intentanim.CustomIntent;


public class WebViewActivity extends AppCompatActivity {

    WebView mainWebView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_web_view);

        String actionGame  = getIntent().getExtras().getString("action_link");
        String puzzleGame  = getIntent().getExtras().getString("puzzle_link");
        String arcadeGame  = getIntent().getExtras().getString("arcade_link");
        String adventureGame  = getIntent().getExtras().getString("adventure_link");
        String sportsGame  = getIntent().getExtras().getString("sports_link");
        String racingGame  = getIntent().getExtras().getString("racing_link");
        String zombieGame  = getIntent().getExtras().getString("zombie_link");

        mainWebView = (WebView)findViewById(R.id.webview);
        mainWebView.setWebViewClient(new myWebclient());

        mainWebView.loadUrl(actionGame);
        mainWebView.loadUrl(puzzleGame);
        mainWebView.loadUrl(arcadeGame);
        mainWebView.loadUrl(adventureGame);
        mainWebView.loadUrl(sportsGame);
        mainWebView.loadUrl(racingGame);
        mainWebView.loadUrl(zombieGame);

        WebSettings webSettings = mainWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        mainWebView.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
        webSettings.setEnableSmoothTransition(true);
    }

    public void finish (){
        super.finish();
        CustomIntent.customType(this,"fadein-to-fadeout");
    }

    public class myWebclient extends WebViewClient {

        @Override
        public void onReceivedError(WebView mainWebView, int errorCode, String description, String failingUrl) {
            //Clearing the WebView
            try {
                mainWebView.stopLoading();
            } catch (Exception e) {
            }
            try {
                mainWebView.clearView();
            } catch (Exception e) {
            }
            if (mainWebView.canGoBack()) {
                mainWebView.goBack();
            }
            mainWebView.loadUrl("file:///android_asset/nointernet.html");

            AlertDialog alertDialog = new AlertDialog.Builder(WebViewActivity.this).create();
            View view = LayoutInflater.from(WebViewActivity.this).inflate(R.layout.internet_error, null);
            TextView title = (TextView) view.findViewById(R.id.title);
            ImageButton imageButton = (ImageButton) view.findViewById(R.id.image);
            title.setText("No Internet Access!");
            imageButton.setImageResource(R.drawable.no_net);

            alertDialog.setButton(Dialog.BUTTON_POSITIVE, "Try Again", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                    startActivity(getIntent());
                }
            });

            alertDialog.setView(view);
            alertDialog.show();
            Toast.makeText(getApplicationContext(),"No Internet",Toast.LENGTH_SHORT).show();
            super.onReceivedError(mainWebView, errorCode, description, failingUrl);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {

        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return super.shouldOverrideUrlLoading(view, url);
        }
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mainWebView.canGoBack()) {
            mainWebView.goBack();
        } else {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            View view = LayoutInflater.from(WebViewActivity.this).inflate(R.layout.close_alert, null);
            TextView title = (TextView) view.findViewById(R.id.title);
            ImageButton imageButton = (ImageButton) view.findViewById(R.id.image);
            title.setText("Hello There!");
            imageButton.setImageResource(R.drawable.exit);

            dialog.setPositiveButton(Html.fromHtml("<font color='#D800FF'>EXIT ME</font>"), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });

            dialog.setCancelable(false);
            dialog.setNegativeButton(Html.fromHtml("<font color='#FF7F27'>NOT NOW</font>"), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            dialog.setView(view);
            dialog.show();
        }
    }
}