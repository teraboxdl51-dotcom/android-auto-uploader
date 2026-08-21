package com.example.androidautouploader;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(
                    new Intent(this, UploadService.class)
            );
        } else {
            startService(
                    new Intent(this, UploadService.class)
            );
        }

        finish();
    }
}
