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

    private static final int NOTIFICATION_ID =
            1001;

    private static final String RAILWAY_URL =
            "https://telegram-auto-uploader-production.up.railway.app/upload";

    private static final String PREFS =
            "auto_uploader_settings";

    private static final String FOLDER_URI =
            "folder_uri";

    private static final String UPLOADED_FILES =
            "uploaded_files";

    private static final String ACTION_CANCEL =
            "com.example.androidautouploader.CANCEL_UPLOAD";

    private static final String ACTION_PAUSE =
            "com.example.androidautouploader.PAUSE_UPLOAD";

    private static final String ACTION_RESUME =
            "com.example.androidautouploader.RESUME_UPLOAD";

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
                                | PendingIntent.FLAG_IMMUTABLE
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
                                | PendingIntent.FLAG_IMMUTABLE
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
                                true
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
                            .Document.COLUMN_DOCUMENT_ID,

                    DocumentsContract
                            .Document.COLUMN_DISPLAY_NAME,

                    DocumentsContract
                            .Document.COLUMN_MIME_TYPE,

                    DocumentsContract
                            .Document.COLUMN_SIZE
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

                long size =
                        0;

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

                    showComplete(
                            filename
                    );

                    sleepSafe(
                            1200
                    );

                    notifyWatching();

                } else {

                    showFailed(
                            filename
                    );

                    sleepSafe(
                            1500
                    );

                    notifyWatching();
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            if (cursor != null) {

                cursor.close();
            }
        }
    }

    // =========================================================
    // UPLOAD FILE
    // =========================================================

    private boolean uploadFile(
            Uri uri,
            String filename,
            String mimeType,
            long totalSize
    ) {

        HttpURLConnection connection = null;

        String boundary =
                "----AutoUploaderBoundary"
                        + System.currentTimeMillis();

        DataOutputStream output = null;

        try {

            URL url =
                    new URL(
                            RAILWAY_URL
                    );

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            currentConnection =
                    connection;

            connection.setRequestMethod(
                    "POST"
            );

            connection.setDoOutput(
                    true
            );

            connection.setDoInput(
                    true
            );

            connection.setUseCaches(
                    false
            );

            connection.setConnectTimeout(
                    30000
            );

            connection.setReadTimeout(
                    3600000
            );

            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary="
                            + boundary
            );

            output =
                    new DataOutputStream(
                            connection
                                    .getOutputStream()
                    );

            output.writeBytes(
                    "--"
                            + boundary
                            + "\r\n"
            );

            output.writeBytes(
                    "Content-Disposition: form-data; "
                            + "name=\"file\"; filename=\""
                            + filename
                            + "\"\r\n"
            );

            String safeMime =
                    mimeType;

            if (safeMime == null ||
                    safeMime.isEmpty()) {

                safeMime =
                        guessMimeType(
                                filename
                        );
            }

            if (safeMime == null ||
                    safeMime.isEmpty()) {

                safeMime =
                        "application/octet-stream";
            }

            output.writeBytes(
                    "Content-Type: "
                            + safeMime
                            + "\r\n"
            );

            output.writeBytes(
                    "\r\n"
            );

            ContentResolver resolver =
                    getContentResolver();

            InputStream input =
                    resolver.openInputStream(
                            uri
                    );

            if (input == null) {

                output.close();

                return false;
            }

            long uploadedBytes = 0;

            long startTime =
                    System.currentTimeMillis();

            int lastProgress = -1;

            long lastUpdateTime =
                    startTime;

            long lastUploadedBytes =
                    0;

            try (
                    BufferedInputStream buffered =
                            new BufferedInputStream(
                                    input
                            )
            ) {

                byte[] buffer =
                        new byte[
                                1024 * 1024
                        ];

                int bytesRead;

                while (
                        running
                                &&
                        (
                                bytesRead =
                                        buffered.read(
                                                buffer
                                        )
                        ) != -1
                ) {

                    // =================================================
                    // PAUSE
                    // =================================================

                    while (
                            running
                                    &&
                            paused
                    ) {

                        notifyStatus(
                                "⏸ Paused: "
                                        + filename,
                                lastProgress < 0
                                        ? 0
                                        : lastProgress
                        );

                        sleepSafe(
                                300
                        );
                    }

                    if (!running) {

                        return false;
                    }

                    output.write(
                            buffer,
                            0,
                            bytesRead
                    );

                    uploadedBytes +=
                            bytesRead;

                    int progress = 0;

                    if (totalSize > 0) {

                        progress =
                                (int)
                                        Math.min(
                                                100,
                                                (
                                                        uploadedBytes
                                                                * 100L
                                                )
                                                        / totalSize
                                        );
                    }

                    long now =
                            System.currentTimeMillis();

                    long elapsed =
                            now
                                    - lastUpdateTime;

                    if (progress !=
                            lastProgress
                            ||
                            elapsed >= 1000) {

                        lastProgress =
                                progress;

                        double speed =
                                0;

                        long speedBytes =
                                uploadedBytes
                                        - lastUploadedBytes;

                        if (elapsed > 0) {

                            speed =
                                    (
                                            speedBytes
                                                    * 1000.0
                                    )
                                            / elapsed;
                        }

                        String speedText =
                                formatSpeed(
                                        speed
                                );

                        String sizeText =
                                formatSize(
                                        uploadedBytes
                                )
                                        + " / "
                                        + formatSize(
                                        totalSize
                                );

                        notifyStatus(
                                "📤 "
                                        + filename
                                        + " • "
                                        + progress
                                        + "% • "
                                        + speedText
                                        + " • "
                                        + sizeText,
                                progress
                        );

                        lastUpdateTime =
                                now;

                        lastUploadedBytes =
                                uploadedBytes;
                    }
                }
            }

            if (!running) {

                return false;
            }

            output.writeBytes(
                    "\r\n"
            );

            output.writeBytes(
                    "--"
                            + boundary
                            + "--\r\n"
            );

            output.flush();

            output.close();

            output = null;

            int responseCode =
                    connection
                            .getResponseCode();

            return responseCode >= 200
                    &&
                    responseCode < 300;

        } catch (Exception e) {

            e.printStackTrace();

            return false;

        } finally {

            currentConnection =
                    null;

            if (output != null) {

                try {

                    output.close();

                } catch (Exception ignored) {
                }
            }

            if (connection != null) {

                try {

                    connection.disconnect();

                } catch (Exception ignored) {
                }
            }
        }
    }

    // =========================================================
    // DELETE ORIGINAL
    // =========================================================

    private void deleteOriginal(
            Uri uri
    ) {

        try {

            boolean deleted =
                    DocumentsContract
                            .deleteDocument(
                                    getContentResolver(),
                                    uri
                            );

            if (!deleted) {

                try {

                    getContentResolver()
                            .delete(
                                    uri,
                                    null,
                                    null
                            );

                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // =========================================================
    // PAUSE
    // =========================================================

    private void pauseUpload() {

        paused = true;

        notifyStatus(
                "⏸ Upload paused",
                0
        );
    }

    // =========================================================
    // RESUME
    // =========================================================

    private void resumeUpload() {

        paused = false;

        notifyWatching();
    }

    // =========================================================
    // CANCEL
    // =========================================================

    private void cancelUpload() {

        running = false;

        uploading = false;

        paused = false;

        handler.removeCallbacks(
                scanner
        );

        HttpURLConnection connection =
                currentConnection;

        if (connection != null) {

            try {

                connection.disconnect();

            } catch (Exception ignored) {
            }
        }

        executor.shutdownNow();

        showResult(
                "⛔ Upload cancelled"
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.N) {

            stopForeground(
                    STOP_FOREGROUND_REMOVE
            );

        } else {

            stopForeground(
                    true
            );
        }

        stopSelf();
    }

    // =========================================================
    // SERVICE COMMAND
    // =========================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent != null) {

            String action =
                    intent.getAction();

            if (ACTION_CANCEL.equals(
                    action
            )) {

                cancelUpload();

                return START_NOT_STICKY;
            }

            if (ACTION_PAUSE.equals(
                    action
            )) {

                pauseUpload();

                return START_STICKY;
            }

            if (ACTION_RESUME.equals(
                    action
            )) {

                resumeUpload();

                return START_STICKY;
            }
        }

        return START_STICKY;
    }

    // =========================================================
    // COMPLETE
    // =========================================================

    private void showComplete(
            String filename
    ) {

        Notification notification =
                createNotification(
                        "✅ Uploaded & deleted: "
                                + filename,
                        100,
                        true,
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
    }

    // =========================================================
    // FAILED
    // =========================================================

    private void showFailed(
            String filename
    ) {

        Notification notification =
                createNotification(
                        "❌ Upload failed: "
                                + filename,
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
    }

    // =========================================================
    // RESULT
    // =========================================================

    private void showResult(
            String text
    ) {

        Notification notification =
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
                                false
                        )
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_LOW
                        )
                        .build();

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

    // =========================================================
    // MIME TYPE
    // =========================================================

    private String guessMimeType(
            String filename
    ) {

        String extension =
                MimeTypeMap
                        .getFileExtensionFromUrl(
                                filename
                        );

        if (extension == null ||
                extension.isEmpty()) {

            int dot =
                    filename.lastIndexOf(
                            '.'
                    );

            if (dot >= 0 &&
                    dot < filename.length() - 1) {

                extension =
                        filename.substring(
                                dot + 1
                        );
            }
        }

        if (extension != null) {

            String mime =
                    MimeTypeMap
                            .getSingleton()
                            .getMimeTypeFromExtension(
                                    extension
                            );

            if (mime != null) {

                return mime;
            }
        }

        return "application/octet-stream";
    }

    // =========================================================
    // UPLOADED FILE DATABASE
    // =========================================================

    private void loadUploadedFiles() {

        uploadedFiles.clear();

        String saved =
                preferences.getString(
                        UPLOADED_FILES,
                        ""
                );

        if (saved.isEmpty()) {

            return;
        }

        String[] values =
                saved.split(
                        "\\n"
                );

        for (String value :
                values) {

            if (!value.trim().isEmpty()) {

                uploadedFiles.add(
                        value
                );
            }
        }
    }

    private void saveUploadedFiles() {

        StringBuilder builder =
                new StringBuilder();

        for (String value :
                uploadedFiles) {

            builder.append(
                    value
            );

            builder.append(
                    "\n"
            );
        }

        preferences
                .edit()
                .putString(
                        UPLOADED_FILES,
                        builder.toString()
                )
                .apply();
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
    // FORMAT SPEED
    // =========================================================

    private String formatSpeed(
            double bytesPerSecond
    ) {

        if (bytesPerSecond <= 0) {

            return "0 B/s";
        }

        return formatSize(
                (long)
                        bytesPerSecond
        )
                + "/s";
    }

    // =========================================================
    // SLEEP
    // =========================================================

    private void sleepSafe(
            long milliseconds
    ) {

        try {

            Thread.sleep(
                    milliseconds
            );

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();
        }
    }

    // =========================================================
    // TIMEOUT
    // =========================================================

    @Override
    public void onTimeout(
            int startId,
            int fgsType
    ) {

        running = false;

        uploading = false;

        paused = false;

        handler.removeCallbacks(
                scanner
        );

        HttpURLConnection connection =
                currentConnection;

        if (connection != null) {

            try {

                connection.disconnect();

            } catch (Exception ignored) {
            }
        }

        executor.shutdownNow();

        stopSelf();

        super.onTimeout(
                startId,
                fgsType
        );
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        running = false;

        uploading = false;

        paused = false;

        handler.removeCallbacks(
                scanner
        );

        HttpURLConnection connection =
                currentConnection;

        if (connection != null) {

            try {

                connection.disconnect();

            } catch (Exception ignored) {
            }
        }

        executor.shutdownNow();

        super.onDestroy();
    }

    // =========================================================
    // BIND
    // =========================================================

    @Nullable
    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
                }
