package com.example.sanivend;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Welcome page: the SaniVend logo, shown every time the app starts.
 * After a short animation it opens Login (MainActivity), which goes straight on to
 * Sanivend Machines when the user is already signed in.
 */
public class WelcomeActivity extends AppCompatActivity {

    /** How long the welcome page stays on screen. */
    static final long SHOW_MS = 1800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = this::openApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        View logo = findViewById(R.id.welcome_logo);
        View name = findViewById(R.id.welcome_name);
        View tagline = findViewById(R.id.welcome_tagline);

        logo.setAlpha(0f);
        logo.setScaleX(0.8f);
        logo.setScaleY(0.8f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start();
        name.setAlpha(0f);
        name.animate().alpha(1f).setStartDelay(350).setDuration(600).start();
        tagline.setAlpha(0f);
        tagline.animate().alpha(1f).setStartDelay(600).setDuration(600).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(next, SHOW_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(next);       // don't jump ahead while the app is in the background
    }

    private void openApp() {
        if (isFinishing()) return;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
