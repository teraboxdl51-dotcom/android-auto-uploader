package com.example.androidautouploader;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setText("Telegram Auto Uploader");
        title.setTextSize(24);

        Button startButton = new Button(this);
        startButton.setText("START AUTO UPLOAD");

        Button stopButton = new Button(this);
        stopButton.setText("STOP AUTO UPLOAD");

        layout.addView(title);
        layout.addView(startButton);
        layout.addView(stopButton);

        setContentView(layout);

        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, UploadService.class);
            startForegroundService(intent);
        });

        stopButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, UploadService.class);
            stopService(intent);
        });
    }
}
