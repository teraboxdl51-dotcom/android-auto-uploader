package com.example.androidautouploader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION = 100;
    private static final int REQUEST_MEDIA = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Simple stable UI
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 60, 30, 30);

        TextView title = new TextView(this);
        title.setText("Auto Uploader\n\nStarting upload service...");
        title.setTextSize(20);

        layout.addView(title);

        setContentView(layout);

        requestPermissionsAndStart();
    }

    private void requestPermissionsAndStart() {

        // Android 13+
        if (Build.VERSION.SDK_INT >= 33) {

            boolean notification =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED;

            boolean media =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED;

            if (!notification) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        REQUEST_NOTIFICATION
                );

                return;
            }

            if (!media) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_MEDIA_VIDEO
                        },
                        REQUEST_MEDIA
                );

                return;
            }

            startUploaderService();

        } else {

            boolean storage =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED;

            if (!storage) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        REQUEST_MEDIA
                );

                return;
            }

            startUploaderService();
        }
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

        if (requestCode == REQUEST_NOTIFICATION ||
                requestCode == REQUEST_MEDIA) {

            requestPermissionsAndStart();
        }
    }

    private void startUploaderService() {

        try {

            Intent intent =
                    new Intent(
                            this,
                            UploadService.class
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                ContextCompat.startForegroundService(
                        this,
                        intent
                );

            } else {

                startService(intent);
            }

        } catch (Exception e) {

            TextView error =
                    new TextView(this);

            error.setText(
                    "Service start error:\n\n"
                            + e.getClass().getSimpleName()
                            + "\n\n"
                            + e.getMessage()
            );

            error.setTextSize(16);

            setContentView(error);
        }
    }
}
