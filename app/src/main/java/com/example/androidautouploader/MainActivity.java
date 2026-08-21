package com.example.androidautouploader;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 80, 40, 40);

        TextView title = new TextView(this);

        title.setText(
                "Telegram Auto Uploader\n\n" +
                "APP TEST MODE\n\n" +
                "App opened successfully.\n\n" +
                "Upload service is temporarily OFF."
        );

        title.setTextSize(20);
        title.setTextColor(Color.BLACK);

        layout.addView(title);

        setContentView(layout);
    }
}
