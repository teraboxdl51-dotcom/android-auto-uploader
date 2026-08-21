package com.example.androidautouploader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= 33) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        REQUEST_NOTIFICATION
                );
            }
        }

        // Start upload service
        startUploadService();

        // Keep the app screen open
        setContentView(
                new android.widget.TextView(this) {{
                    setText(
                            "Auto Uploader\n\n" +
                            "Watching:\n" +
                            "Download/VideoDownloader/\n\n" +
                            "Videos will upload automatically."
                    );
                    setTextSize(18);
                    setPadding(30, 30, 30, 30);
                }}
        );
    }

    private void startUploadService() {

        Intent intent =
                new Intent(
                        MainActivity.this,
                        UploadService.class
                );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            startForegroundService(intent);

        } else {

            startService(intent);
        }
    }

    @Override
    protected void onDestroy() {

        // DON'T stop UploadService here.
        // Service must continue in background.

        super.onDestroy();
    }
}
