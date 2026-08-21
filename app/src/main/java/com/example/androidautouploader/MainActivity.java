package com.example.androidautouploader;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startUploaderService();
    }

    private void startUploaderService() {

        Intent serviceIntent =
                new Intent(this, UploadService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // App screen close ചെയ്യാം,
        // service background-ൽ തുടരാൻ ശ്രമിക്കും.
        finish();
    }
}
