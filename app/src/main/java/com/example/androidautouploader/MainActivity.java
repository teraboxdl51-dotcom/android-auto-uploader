package com.example.androidautouploader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setText(
                "Auto Uploader\n\n" +
                "Service is starting...\n\n" +
                "Videos from Download / Movies will be uploaded automatically."
        );
        textView.setTextSize(18);
        textView.setPadding(40, 80, 40, 40);
        setContentView(textView);

        requestPermissionsIfNeeded();
    }

    private void requestPermissionsIfNeeded() {

        if (Build.VERSION.SDK_INT >= 33) {

            if (checkSelfPermission(
                    Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        PERMISSION_REQUEST
                );

                return;
            }
        }

        startUploader();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISSION_REQUEST) {
            startUploader();
        }
    }

    private void startUploader() {

        Intent serviceIntent =
                new Intent(this, UploadService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}
