package com.example.androidautouploader;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private static final int FOLDER_REQUEST_CODE = 200;

    private static final String PREFS =
            "auto_uploader_settings";

    private static final String FOLDER_URI =
            "folder_uri";

    private SharedPreferences preferences;

    private TextView statusText;
    private TextView folderText;
    private TextView uploadText;
    private TextView speedText;
    private ProgressBar progressBar;

    private Button pauseButton;
    private Button resumeButton;
    private Button cancelButton;

    private final BroadcastReceiver progressReceiver =
            new BroadcastReceiver() {

        @Override
        public void onReceive(
                Context context,
                Intent intent
        ) {

            if (!UploadService.ACTION_PROGRESS.equals(
                    intent.getAction())) {
                return;
            }

            String filename =
                    intent.getStringExtra(
                            UploadService.EXTRA_FILENAME
                    );

            int progress =
                    intent.getIntExtra(
                            UploadService.EXTRA_PROGRESS,
                            0
                    );

            long uploaded =
                    intent.getLongExtra(
                            UploadService.EXTRA_UPLOADED,
                            0
                    );

            long total =
                    intent.getLongExtra(
                            UploadService.EXTRA_TOTAL,
                            0
                    );

            String speed =
                    intent.getStringExtra(
                            UploadService.EXTRA_SPEED
                    );

            String status =
                    intent.getStringExtra(
                            UploadService.EXTRA_STATUS
                    );

            if (filename == null) {
                filename = "Unknown file";
            }

            if (speed == null) {
                speed = "0 B/s";
            }

            if (uploadText != null) {
                uploadText.setText(
                        "📤 " + filename
                );
            }

            if (progressBar != null) {
                progressBar.setProgress(
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        progress
                                )
                        )
                );
            }

            if (speedText != null) {
                speedText.setText(
                        "Speed: " + speed +
                        "\nUploaded: " +
                        formatSize(uploaded) +
                        " / " +
                        formatSize(total)
                );
            }

            if (statusText != null) {

                if ("uploading".equals(status)) {

                    statusText.setText(
                            "🟢 Uploading..."
                    );

                } else if ("paused".equals(status)) {

                    statusText.setText(
                            "⏸ Upload paused"
                    );

                } else if ("complete".equals(status)) {

                    statusText.setText(
                            "✅ Upload complete"
                    );

                } else if ("cancelled".equals(status)) {

                    statusText.setText(
                            "⛔ Upload cancelled"
                    );

                } else if ("failed".equals(status)) {

                    statusText.setText(
                            "🔴 Upload failed"
                    );
                }
            }

            if (pauseButton != null) {
                pauseButton.setEnabled(
                        "uploading".equals(status)
                );
            }

            if (resumeButton != null) {
                resumeButton.setEnabled(
                        "paused".equals(status)
                );
            }

            if (cancelButton != null) {
                cancelButton.setEnabled(
                        "uploading".equals(status) ||
                        "paused".equals(status)
                );
            }
        }
    };

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        preferences =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        createScreen();

        IntentFilter progressFilter =
                new IntentFilter(
                        UploadService.ACTION_PROGRESS
                );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            registerReceiver(
                    progressReceiver,
                    progressFilter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    progressReceiver,
                    progressFilter
            );
        }

        requestNotificationPermissionIfNeeded();
    }

    // =========================================================
    // MAIN SCREEN
    // =========================================================

    private void createScreen() {

        ScrollView scrollView =
                new ScrollView(this);

        LinearLayout mainLayout =
                new LinearLayout(this);

        mainLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        mainLayout.setPadding(
                30,
                50,
                30,
                40
        );

        mainLayout.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        // =====================================================
        // TITLE
        // =====================================================

        TextView title =
                new TextView(this);

        title.setText(
                "🚀 Telegram Auto Uploader"
        );

        title.setTextSize(27);

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                20,
                0,
                15
        );

        mainLayout.addView(
                title,
                fullWidth()
        );

        // =====================================================
        // SERVICE STATUS
        // =====================================================

        statusText =
                new TextView(this);

        statusText.setText(
                "🟢 Auto Uploader starting..."
        );

        statusText.setTextSize(18);

        statusText.setGravity(
                Gravity.CENTER
        );

        statusText.setPadding(
                0,
                10,
                0,
                30
        );

        mainLayout.addView(
                statusText,
                fullWidth()
        );

        // =====================================================
        // FOLDER
        // =====================================================

        TextView folderTitle =
                new TextView(this);

        folderTitle.setText(
                "📂 Upload Folder"
        );

        folderTitle.setTextSize(21);

        mainLayout.addView(
                folderTitle,
                fullWidth()
        );

        folderText =
                new TextView(this);

        String savedFolder =
                preferences.getString(
                        FOLDER_URI,
                        ""
                );

        if (savedFolder.isEmpty()) {

            folderText.setText(
                    "No folder selected"
            );

        } else {

            folderText.setText(
                    "✅ VideoDownloader folder selected"
            );
        }

        folderText.setTextSize(16);

        folderText.setPadding(
                0,
                15,
                0,
                15
        );

        mainLayout.addView(
                folderText,
                fullWidth()
        );

        Button folderButton =
                new Button(this);

        folderButton.setText(
                "📂 Select VideoDownloader Folder"
        );

        folderButton.setOnClickListener(
                v -> openFolderPicker()
        );

        mainLayout.addView(
                folderButton,
                fullWidth()
        );

        // =====================================================
        // CURRENT FILE
        // =====================================================

        TextView currentTitle =
                new TextView(this);

        currentTitle.setText(
                "\n📤 Current Upload"
        );

        currentTitle.setTextSize(21);

        mainLayout.addView(
                currentTitle,
                fullWidth()
        );

        uploadText =
                new TextView(this);

        uploadText.setText(
                "No active upload"
        );

        uploadText.setTextSize(17);

        uploadText.setPadding(
                0,
                15,
                0,
                10
        );

        mainLayout.addView(
                uploadText,
                fullWidth()
        );

        // =====================================================
        // PROGRESS BAR
        // =====================================================

        progressBar =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr.progressBarStyleHorizontal
                );

        progressBar.setMax(
                100
        );

        progressBar.setProgress(
                0
        );

        LinearLayout.LayoutParams progressParams =
                fullWidth();

        progressParams.setMargins(
                0,
                10,
                0,
                10
        );

        mainLayout.addView(
                progressBar,
                progressParams
        );

        // =====================================================
        // SPEED
        // =====================================================

        speedText =
                new TextView(this);

        speedText.setText(
                "Speed: --\n" +
                "Uploaded: -- / --"
        );

        speedText.setTextSize(16);

        speedText.setPadding(
                0,
                10,
                0,
                20
        );

        mainLayout.addView(
                speedText,
                fullWidth()
        );

        // =====================================================
        // PAUSE
        // =====================================================

        pauseButton =
                new Button(this);

        pauseButton.setText(
                "⏸ PAUSE"
        );

        pauseButton.setEnabled(
                false
        );

        pauseButton.setOnClickListener(
                v -> sendServiceAction(
                        "com.example.androidautouploader.PAUSE_UPLOAD"
                )
        );

        mainLayout.addView(
                pauseButton,
                fullWidth()
        );

        // =====================================================
        // RESUME
        // =====================================================

        resumeButton =
                new Button(this);

        resumeButton.setText(
                "▶️ RESUME"
        );

        resumeButton.setEnabled(
                false
        );

        resumeButton.setOnClickListener(
                v -> sendServiceAction(
                        "com.example.androidautouploader.RESUME_UPLOAD"
                )
        );

        mainLayout.addView(
                resumeButton,
                fullWidth()
        );

        // =====================================================
        // CANCEL
        // =====================================================

        cancelButton =
                new Button(this);

        cancelButton.setText(
                "❌ CANCEL UPLOAD"
        );

        cancelButton.setEnabled(
                false
        );

        cancelButton.setOnClickListener(
                v -> sendServiceAction(
                        "com.example.androidautouploader.CANCEL_UPLOAD"
                )
        );

        mainLayout.addView(
                cancelButton,
                fullWidth()
        );

        // =====================================================
        // INFO
        // =====================================================

        TextView info =
                new TextView(this);

        info.setText(
                "\nAutomatic mode:\n" +
                "🆕 New files only\n" +
                "🎬 Video\n" +
                "🖼️ Photo\n" +
                "📄 Document\n" +
                "📦 ZIP / other files\n" +
                "✅ Delete only after successful upload"
        );

        info.setTextSize(15);

        info.setPadding(
                0,
                25,
                0,
                20
        );

        mainLayout.addView(
                info,
                fullWidth()
        );

        scrollView.addView(
                mainLayout
        );

        setContentView(
                scrollView
        );
    }

    // =========================================================
    // FOLDER PICKER
    // =========================================================

    private void openFolderPicker() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT_TREE
                );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );

        startActivityForResult(
                intent,
                FOLDER_REQUEST_CODE
        );
    }

    // =========================================================
    // FOLDER RESULT
    // =========================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode !=
                FOLDER_REQUEST_CODE) {

            return;
        }

        if (resultCode !=
                RESULT_OK) {

            return;
        }

        if (data == null ||
                data.getData() == null) {

            return;
        }

        Uri uri =
                data.getData();

        try {

            getContentResolver()
                    .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );

        } catch (Exception e) {

            e.printStackTrace();
        }

        preferences
                .edit()
                .putString(
                        FOLDER_URI,
                        uri.toString()
                )
                .apply();

        folderText.setText(
                "✅ Upload folder selected"
        );

        statusText.setText(
                "🟢 Folder permission saved"
        );

        startUploaderService();
    }

    // =========================================================
    // NOTIFICATION PERMISSION
    // =========================================================

    private void requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
            ) !=
                    PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission
                                        .POST_NOTIFICATIONS
                        },
                        NOTIFICATION_PERMISSION_REQUEST
                );

                return;
            }
        }

        startUploaderService();
    }

    // =========================================================
    // PERMISSION RESULT
    // =========================================================

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

        if (requestCode ==
                NOTIFICATION_PERMISSION_REQUEST) {

            startUploaderService();
        }
    }

    // =========================================================
    // START SERVICE
    // =========================================================

    private void startUploaderService() {

        try {

            Intent serviceIntent =
                    new Intent(
                            this,
                            UploadService.class
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                startForegroundService(
                        serviceIntent
                );

            } else {

                startService(
                        serviceIntent
                );
            }

            statusText.setText(
                    "🟢 Auto Uploader is running"
            );

        } catch (Exception e) {

            e.printStackTrace();

            statusText.setText(
                    "🔴 Failed to start uploader"
            );
        }
    }

    // =========================================================
    // SERVICE ACTION
    // =========================================================

    private void sendServiceAction(
            String action
    ) {

        try {

            Intent intent =
                    new Intent(
                            this,
                            UploadService.class
                    );

            intent.setAction(
                    action
            );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                startForegroundService(
                        intent
                );

            } else {

                startService(
                        intent
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // =========================================================
    // FORMAT SIZE
    // =========================================================

    private String formatSize(
            long bytes
    ) {

        if (bytes < 1024) {

            return bytes + " B";
        }

        double kb =
                bytes / 1024.0;

        if (kb < 1024) {

            return String.format(
                    "%.1f KB",
                    kb
            );
        }

        double mb =
                kb / 1024.0;

        if (mb < 1024) {

            return String.format(
                    "%.1f MB",
                    mb
            );
        }

        double gb =
                mb / 1024.0;

        return String.format(
                "%.2f GB",
                gb
        );
    }

    // =========================================================
    // LAYOUT PARAMS
    // =========================================================

    private LinearLayout.LayoutParams
    fullWidth() {

        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    // =========================================================
    // RESUME UI
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();

        String savedFolder =
                preferences.getString(
                        FOLDER_URI,
                        ""
                );

        if (!savedFolder.isEmpty()) {

            if (folderText != null) {

                folderText.setText(
                        "✅ VideoDownloader folder selected"
                );
            }

            if (statusText != null) {

                statusText.setText(
                        "🟢 Auto Uploader ready"
                );
            }
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        try {

            unregisterReceiver(
                    progressReceiver
            );

        } catch (Exception ignored) {
        }

        super.onDestroy();
    }
}
