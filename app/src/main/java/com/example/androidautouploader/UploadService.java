package com.example.androidautouploader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadService extends Service {

    // =========================================================
    // CONFIG
    // =========================================================

    private static final String CHANNEL_ID =
            "telegram_auto_uploader";

    private static final int NOTIFICATION_ID = 1001;

    private static final String RAILWAY_URL =
            "https://telegram-auto-uploader-production.up.railway.app/upload";

    /*
     * IMPORTANT:
     * Replace this with your NEW Railway API key.
     * Do NOT use the old exposed key.
     */
    private static final String API_KEY =
            "YOUR_RAILWAY_API_KEY";

    private static final String PREFS =
            "auto_uploader_settings";

    private static final String FOLDER_URI =
            "folder_uri";

    private static final String UPLOADED_FILES =
            "uploaded_files";

    // =========================================================
    // SERVICE ACTIONS
    // =========================================================

    public static final String ACTION_CANCEL =
            "com.example.androidautouploader.CANCEL_UPLOAD";

    public static final String ACTION_PAUSE =
            "com.example.androidautouploader.PAUSE_UPLOAD";

    public static final String ACTION_RESUME =
            "com.example.androidautouploader.RESUME_UPLOAD";

    // =========================================================
    // PROGRESS BROADCAST
    // =========================================================

    public static final String ACTION_PROGRESS =
            "com.example.androidautouploader.UPLOAD_PROGRESS";

    public static final String EXTRA_FILENAME =
            "filename";

    public static final String EXTRA_PROGRESS =
            "progress";

    public static final String EXTRA_UPLOADED =
            "uploaded";

    public static final String EXTRA_TOTAL =
            "total";

    public static final String EXTRA_SPEED =
            "speed";

    public static final String EXTRA_STATUS =
            "status";

    // =========================================================
    // STATE
    // =========================================================

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean running = true;

    private volatile boolean uploading = false;

    private volatile boolean paused = false;

    private volatile HttpURLConnection currentConnection;

    private SharedPreferences preferences;

    private final Set<String> uploadedFiles =
            new HashSet<>();

    // =========================================================
    // SCANNER
    // =========================================================

    private final Runnable scanner =
            new Runnable() {

        @Override
        public void run() {

            if (!running) {
                return;
            }

            if (!uploading &&
                    !paused &&
                    !executor.isShutdown()) {

                executor.execute(
                        () -> scanFolder()
                );
            }

            if (running) {

                handler.postDelayed(
                        this,
                        5000
                );
            }
        }
    };

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public void onCreate() {

        super.onCreate();

        running = true;
        paused = false;
        uploading = false;

        preferences =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        loadUploadedFiles();

        createNotificationChannel();

        try {

            Notification notification =
                    createNotification(
                            "🟢 Watching VideoDownloader...",
                            0,
                            false,
                            false
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_DATA_SYNC
                );

            } else {

                startForeground(
                        NOTIFICATION_ID,
                        notification
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            stopSelf();

            return;
        }

        handler.post(scanner);
    }

    // =========================================================
    // NOTIFICATION CHANNEL
    // =========================================================

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Telegram Auto Uploader",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Automatic Telegram upload status"
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }

    // =========================================================
    // NOTIFICATION
    // =========================================================

    private Notification createNotification(
            String text,
            int progress,
            boolean showProgress,
            boolean showCancel
    ) {

        Intent cancelIntent =
                new Intent(
                        this,
                        UploadService.class
                );

        cancelIntent.setAction(
                ACTION_CANCEL
        );

        PendingIntent cancelPendingIntent =
                PendingIntent.getService(
                        this,
                        501,
                        cancelIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        PendingIntent.FLAG_IMMUTABLE
                );

        Intent pauseIntent =
                new Intent(
                        this,
                        UploadService.class
                );

        pauseIntent.setAction(
                paused
                        ? ACTION_RESUME
                        : ACTION_PAUSE
        );

        PendingIntent pausePendingIntent =
                PendingIntent.getService(
                        this,
                        502,
                        pauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        PendingIntent.FLAG_IMMUTABLE
                );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable
                                        .ic_menu_upload
                        )
                        .setContentTitle(
                                "Telegram Auto Uploader"
                        )
                        .setContentText(
                                text
                        )
                        .setOngoing(
                                showProgress
                        )
                        .setOnlyAlertOnce(
                                true
                        )
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_LOW
                        );

        if (showProgress) {

            builder.setProgress(
                    100,
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    progress
                            )
                    ),
                    false
            );

            builder.addAction(
                    paused
                            ? android.R.drawable
                                    .ic_media_play
                            : android.R.drawable
                                    .ic_media_pause,
                    paused
                            ? "RESUME"
                            : "PAUSE",
                    pausePendingIntent
            );

            if (showCancel) {

                builder.addAction(
                        android.R.drawable
                                .ic_menu_close_clear_cancel,
                        "CANCEL",
                        cancelPendingIntent
                );
            }
        }

        return builder.build();
    }

    // =========================================================
    // NOTIFICATION UPDATE
    // =========================================================

    private void notifyStatus(
            String text,
            int progress
    ) {

        if (!running) {
            return;
        }

        Notification notification =
                createNotification(
                        text,
                        progress,
                        true,
                        true
                );

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            manager.notify(
                    NOTIFICATION_ID,
                    notification
            );
        }
    }

    private void notifyWatching() {

        if (!running) {
            return;
        }

        Notification notification =
                createNotification(
                        paused
                                ? "⏸ Upload paused"
                                : "🟢 Watching VideoDownloader...",
                        0,
                        false,
                        false
                );

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            manager.notify(
                    NOTIFICATION_ID,
                    notification
            );
        }

        sendProgressUpdate(
                "",
                0,
                0,
                0,
                "0 B/s",
                paused
                        ? "paused"
                        : "idle"
        );
    }

    // =========================================================
    // SEND LIVE PROGRESS TO MAIN ACTIVITY
    // =========================================================

    private void sendProgressUpdate(
            String filename,
            int progress,
            long uploaded,
            long total,
            String speed,
            String status
    ) {

        Intent intent =
                new Intent(
                        ACTION_PROGRESS
                );

        /*
         * Restrict broadcast to this application.
         */
        intent.setPackage(
                getPackageName()
        );

        intent.putExtra(
                EXTRA_FILENAME,
                filename
        );

        intent.putExtra(
                EXTRA_PROGRESS,
                progress
        );

        intent.putExtra(
                EXTRA_UPLOADED,
                uploaded
        );

        intent.putExtra(
                EXTRA_TOTAL,
                total
        );

        intent.putExtra(
                EXTRA_SPEED,
                speed
        );

        intent.putExtra(
                EXTRA_STATUS,
                status
        );

        sendBroadcast(
                intent
        );
    }

    // =========================================================
    // FOLDER SCAN
    // =========================================================

    private void scanFolder() {

        if (!running ||
                paused ||
                uploading) {

            return;
        }

        String folderString =
                preferences.getString(
                        FOLDER_URI,
                        ""
                );

        if (folderString.isEmpty()) {

            notifyWatching();

            return;
        }

        Uri treeUri;

        try {

            treeUri =
                    Uri.parse(
                            folderString
                    );

        } catch (Exception e) {

            return;
        }

        ContentResolver resolver =
                getContentResolver();

        Cursor cursor = null;

        try {

            Uri childrenUri =
                    DocumentsContract
                            .buildChildDocumentsUriUsingTree(
                                    treeUri,
                                    DocumentsContract
                                            .getTreeDocumentId(
                                                    treeUri
                                            )
                            );

            String[] projection = {

                    DocumentsContract
                            .Document
                            .COLUMN_DOCUMENT_ID,

                    DocumentsContract
                            .Document
                            .COLUMN_DISPLAY_NAME,

                    DocumentsContract
                            .Document
                            .COLUMN_MIME_TYPE,

                    DocumentsContract
                            .Document
                            .COLUMN_SIZE
            };

            cursor =
                    resolver.query(
                            childrenUri,
                            projection,
                            null,
                            null,
                            null
                    );

            if (cursor == null) {
                return;
            }

            int idColumn =
                    cursor.getColumnIndex(
                            DocumentsContract
                                    .Document
                                    .COLUMN_DOCUMENT_ID
                    );

            int nameColumn =
                    cursor.getColumnIndex(
                            DocumentsContract
                                    .Document
                                    .COLUMN_DISPLAY_NAME
                    );

            int mimeColumn =
                    cursor.getColumnIndex(
                            DocumentsContract
                                    .Document
                                    .COLUMN_MIME_TYPE
                    );

            int sizeColumn =
                    cursor.getColumnIndex(
                            DocumentsContract
                                    .Document
                                    .COLUMN_SIZE
                    );

            while (
                    cursor.moveToNext()
                            &&
                    running
                            &&
                    !paused
            ) {

                String documentId =
                        cursor.getString(
                                idColumn
                        );

                String filename =
                        cursor.getString(
                                nameColumn
                        );

                String mimeType =
                        cursor.getString(
                                mimeColumn
                        );

                long size = 0;

                if (sizeColumn >= 0 &&
                        !cursor.isNull(
                                sizeColumn
                        )) {

                    size =
                            cursor.getLong(
                                    sizeColumn
                            );
                }

                // Ignore folders
                if (DocumentsContract
                        .Document
                        .MIME_TYPE_DIR
                        .equals(
                                mimeType
                        )) {

                    continue;
                }

                if (filename == null ||
                        filename.trim().isEmpty()) {

                    continue;
                }

                String fileKey =
                        documentId
                                + "|"
                                + filename
                                + "|"
                                + size;

                // Already successfully uploaded
                if (uploadedFiles.contains(
                        fileKey
                )) {

                    continue;
                }

                Uri fileUri =
                        DocumentsContract
                                .buildDocumentUriUsingTree(
                                        treeUri,
                                        documentId
                                );

                uploading = true;

                sendProgressUpdate(
                        filename,
                        0,
                        0,
                        size,
                        "0 B/s",
                        "uploading"
                );

                boolean result =
                        uploadFile(
                                fileUri,
                                filename,
                                mimeType,
                                size
                        );

                uploading = false;

                if (!running) {
                    return;
                }

                if (result) {

                    uploadedFiles.add(
                            fileKey
                    );

                    saveUploadedFiles();

                    deleteOriginal(
                            fileUri
                    );

                    sendProgressUpdate(
                            filename,
                            100,
                            size,
                            size,
                            "Complete",
                            "complete"
                    );

                 
