package com.example.androidautouploader;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setText("Telegram Auto Uploader");
        title.setTextSize(24);

        Button start = new Button(this);
        start.setText("START AUTO UPLOAD");

        Button stop = new Button(this);
        stop.setText("STOP AUTO UPLOAD");

        layout.addView(title);
        layout.addView(start);
        layout.addView(stop);

        setContentView(layout);

        start.setOnClickListener(v -> startUploader());

        stop.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    UploadService.class
            );
            stopService(intent);
        });
    }

    private void startUploader() {

        if (Build.VERSION.SDK_INT >= 33) {

            if (checkSelfPermission(
                    Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_MEDIA_VIDEO
                        },
                        REQUEST_PERMISSION
                );

                return;
            }
        }

        Intent intent = new Intent(
                this,
                UploadService.class
        );

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
