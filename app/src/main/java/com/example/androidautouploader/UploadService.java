package com.example.androidautouploader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;

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

    private static final String CHANNEL_ID =
            "telegram_auto_uploader";

    private static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_CANCEL =
            "com.example.androidautouploader.CANCEL_UPLOAD";

    private static final String ACTION_PAUSE =
            "com.example.androidautouploader.PAUSE_UPLOAD";

    private static final String ACTION_RESUME =
            "com.example.androidautouploader.RESUME_UPLOAD";

    private static final String RAILWAY_URL =
            "https://telegram-auto-uploader-production.up.railway.app/upload";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean running = true;

    private volatile boolean uploading = false;

    private volatile boolean paused = false;

    private volatile HttpURLConnection currentConnection;

    private final Set<String> uploadedFiles =
            new HashSet<>();

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
                                () -> scanAndUpload()
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

        createNotificationChannel();

        Notification notification =
                createNotification(
                        "Watching VideoDownloader...",
                        0,
                        false,
                        false
                );

        try {

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
            boolean pausedState
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
                        .setContentText(text)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
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

            if (pausedState) {

                Intent resumeIntent =
                        new Intent(
                                this,
                                UploadService.class
                        );

                resumeIntent.setAction(
                        ACTION_RESUME
                );

                PendingIntent resumePendingIntent =
                        PendingIntent.getService(
                                this,
                                502,
                                resumeIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                                        | PendingIntent.FLAG_IMMUTABLE
                        );

                builder.addAction(
                        android.R.drawable
                                .ic_media_play,
                        "RESUME",
                        resumePendingIntent
                );

            } else {

                Intent pauseIntent =
                        new Intent(
                                this,
                                UploadService.class
                        );

                pauseIntent.setAction(
                        ACTION_PAUSE
                );

                PendingIntent pausePendingIntent =
                        PendingIntent.getService(
                                this,
                                503,
                                pauseIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                                        | PendingIntent.FLAG_IMMUTABLE
                        );

                builder.addAction(
                        android.R.drawable
                                .ic_media_pause,
                        "PAUSE",
                        pausePendingIntent
                );
            }

            builder.addAction(
                    android.R.drawable
                            .ic_menu_close_clear_cancel,
                    "CANCEL",
                    cancelPendingIntent
            );
        }

        return builder.build();
    }

    private void updateNotification(
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
                        paused
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

    private void showWatching() {

        if (!running) {
            return;
        }

        Notification notification =
                createNotification(
                        "Watching VideoDownloader...",
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

    private void showResult(
            String message
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
                                message
                        )
                        .setOnlyAlertOnce(true)
                        .setOngoing(false)
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
    // SCAN ALL FILE TYPES
    // =========================================================

    private void scanAndUpload() {

        if (!running ||
                uploading ||
                paused) {

            return;
        }

        ContentResolver resolver =
                getContentResolver();

        String[] projection = {

                MediaStore.Files.FileColumns._ID,

                MediaStore.Files.FileColumns.DISPLAY_NAME,

                MediaStore.Files.FileColumns.RELATIVE_PATH,

                MediaStore.Files.FileColumns.SIZE,

                MediaStore.Files.FileColumns.DATE_MODIFIED,

                MediaStore.Files.FileColumns.MIME_TYPE
        };

        String selection =
                MediaStore.Files.FileColumns.RELATIVE_PATH
                        + " LIKE ?";

        String[] selectionArgs = {
                "Download/VideoDownloader/%"
        };

        Cursor cursor = null;

        try {

            cursor =
                    resolver.query(
                            MediaStore.Files
                                    .getContentUri(
                                            "external"
                                    ),
                            projection,
                            selection,
                            selectionArgs,
                            MediaStore.Files.FileColumns
                                    .DATE_MODIFIED
                                    + " ASC"
                    );

            if (cursor == null) {

                return;
            }

            int idColumn =
                    cursor.getColumnIndex(
                            MediaStore.Files.FileColumns._ID
                    );

            int nameColumn =
                    cursor.getColumnIndex(
                            MediaStore.Files.FileColumns
                                    .DISPLAY_NAME
                    );

            int sizeColumn =
                    cursor.getColumnIndex(
                            MediaStore.Files.FileColumns
                                    .SIZE
                    );

            int modifiedColumn =
                    cursor.getColumnIndex(
                            MediaStore.Files.FileColumns
                                    .DATE_MODIFIED
                    );

            int mimeColumn =
                    cursor.getColumnIndex(
                            MediaStore.Files.FileColumns
                                    .MIME_TYPE
                    );

            if (idColumn < 0 ||
                    nameColumn < 0 ||
                    sizeColumn < 0) {

                return;
            }

            while (
                    cursor.moveToNext()
                            && running
                            && !paused
            ) {

                long id =
                        cursor.getLong(
                                idColumn
                        );

                String filename =
                        cursor.getString(
                                nameColumn
                        );

                long totalSize =
                        cursor.getLong(
                                sizeColumn
                        );

                long modified =
                        modifiedColumn >= 0
                                ? cursor.getLong(
                                        modifiedColumn
                                )
                                : 0;

                String mimeType =
                        mimeColumn >= 0
                                ? cursor.getString(
                                        mimeColumn
                                )
                                : "application/octet-stream";

                String fileKey =
                        filename
                                + "|"
                                + totalSize
                                + "|"
                                + modified;

                /*
                 * Already uploaded successfully.
                 */
                synchronized (uploadedFiles) {

                    if (uploadedFiles.contains(
                            fileKey
                    )) {

                        continue;
                    }
                }

                Uri uri =
                        ContentUris.withAppendedId(
                                MediaStore.Files
                                        .getContentUri(
                                                "external"
                                        ),
                                id
                        );

                /*
                 * Give newly-created files a little time
                 * to finish writing.
                 */
                if (!isFileReady(
                        uri,
                        totalSize
                )) {

                    continue;
                }

                uploading = true;

                boolean result =
                        uploadFile(
                                uri,
                                filename,
                                mimeType,
                                totalSize
                        );

                uploading = false;

                if (!running) {

                    return;
                }

                if (result) {

                    synchronized (uploadedFiles) {

                        uploadedFiles.add(
                                fileKey
                        );
                    }

                    /*
                     * Delete ONLY after successful upload.
                     */
                    try {

                        int deleted =
                                resolver.delete(
                                        uri,
                                        null,
                                        null
                                );

                        if (deleted > 0) {

                            showResult(
                                    "✅ Uploaded + deleted: "
                                            + filename
                            );

                        } else {

                            showResult(
                                    "✅ Uploaded: "
                                            + filename
                                            + " | Delete failed"
                            );
                        }

                    } catch (Exception e) {

                        e.printStackTrace();

                        showResult(
                                "✅ Uploaded: "
                                        + filename
                                        + " | Delete failed"
                        );
                    }

                    sleepSafe(1500);

                    showWatching();

                } else {

                    showResult(
                            "❌ Upload failed: "
                                    + filename
                    );

                    sleepSafe(1500);

                    showWatching();
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
    // FILE READY CHECK
    // =========================================================

    private boolean isFileReady(
            Uri uri,
            long expectedSize
    ) {

        try {

            ContentResolver resolver =
                    getContentResolver();

            long actualSize = 0;

            try (
                    InputStream input =
                            resolver.openInputStream(
                                    uri
                            )
            ) {

                if (input == null) {

                    return false;
                }

                byte[] buffer =
                        new byte[64 * 1024];

                int read;

                while (
                        (read =
                                input.read(
                                        buffer
                                )) != -1
                ) {

                    actualSize += read;

                    /*
                     * No need to read huge files completely.
                     * We only need to confirm the file is readable.
                     */
                    if (actualSize >=
                            Math.min(
                                    expectedSize,
                                    1024L * 1024L
                            )) {

                        break;
                    }
                }
            }

            return expectedSize <= 0
                    || actualSize > 0;

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // UPLOAD
    // =========================================================

    private boolean uploadFile(
            Uri uri,
            String filename,
            String mimeType,
            long totalSize
    ) {

        HttpURLConnection connection =
                null;

        String boundary =
                "----AutoUploaderBoundary123456789";

        long uploadStart =
                System.currentTimeMillis();

        long lastUpdateTime =
                uploadStart;

        long lastUploadedBytes =
                0;

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

            DataOutputStream output =
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

            output.writeBytes(
                    "Content-Type: "
                            + (
                            mimeType != null
                                    ? mimeType
                                    : "application/octet-stream"
                    )
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

                long uploadedBytes =
                        0;

                int bytesRead;

                int lastProgress =
                        -1;

                while (
                        running
                                &&
                        !paused
                                &&
                        (
                                bytesRead =
                                        buffered.read(
                                                buffer
                                        )
                        ) != -1
                ) {

                    output.write(
                            buffer,
                            0,
                            bytesRead
                    );

                    uploadedBytes +=
                            bytesRead;

                    long now =
                            System.currentTimeMillis();

                    if (
                            now -
                                    lastUpdateTime
                                    >=
                                    500
                    ) {

                        long deltaBytes =
                                uploadedBytes
                                        -
                                        lastUploadedBytes;

                        long deltaTime =
                                now -
                                        lastUpdateTime;

                        double speed =
                                deltaTime > 0
                                        ? (
                                        deltaBytes
                                                * 1000.0
                                )
                                        / deltaTime
                                        : 0;

                        int progress;

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

                        } else {

                            progress = 0;
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

                        final int p =
                                progress;

                        final String status =
                                "Uploading: "
                                        + filename
                                        + " • "
                                        + p
                                        + "% • "
                                        + speedText
                                        + " • "
                                        + sizeText;

                        handler.post(
                                () ->
                                        updateNotification(
                                                status,
                                                p
                                        )
                        );

                        lastUpdateTime =
                                now;

                        lastUploadedBytes =
                                uploadedBytes;

                        lastProgress =
                                progress;
                    }
                }

                /*
                 * PAUSED
                 */
                if (paused) {

                    try {
                        output.close();
                    } catch (Exception ignored) {
                    }

                    return false;
                }

                /*
                 * CANCELLED / SERVICE STOPPED
                 */
                if (!running) {

                    try {
                        output.close();
                    } catch (Exception ignored) {
                    }

                    return false;
                }

                /*
                 * Finish multipart body.
                 */
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
            }

            int responseCode =
                    connection
                            .getResponseCode();

            return responseCode >= 200
                    && responseCode < 300;

        } catch (Exception e) {

            e.printStackTrace();

            return false;

        } finally {

            currentConnection =
                    null;

            if (connection != null) {

                try {

                    connection.disconnect();

                } catch (Exception ignored) {
                }
            }
        }
    }

    // =========================================================
    // PAUSE
    // =========================================================

    private void pauseUpload() {

        if (!uploading) {

            return;
        }

        paused = true;

        HttpURLConnection connection =
                currentConnection;

        if (connection != null) {

            try {

                connection.disconnect();

            } catch (Exception ignored) {
            }
        }

        updateNotification(
                "⏸ Upload paused",
                0
        );
    }

    // =========================================================
    // RESUME
    // =========================================================

    private void resumeUpload() {

        if (!paused) {

            return;
        }

        paused = false;

        updateNotification(
                "▶️ Resuming...",
                0
        );

        /*
         * Scanner will pick the file again.
         * Because Railway does not currently provide
         * resumable upload, the file is uploaded again
         * from the beginning.
         */
        handler.post(
                scanner
        );
    }

    // =========================================================
    // CANCEL
    // =========================================================

    private void cancelUpload() {

        running = false;

        paused = false;

        uploading = false;

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
                "⛔ Upload cancelled — original kept"
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
    // COMMAND
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
    // TIMEOUT
    // =========================================================

    @Override
    public void onTimeout(
            int startId,
            int fgsType
    ) {

        running = false;

        paused = false;

        uploading = false;

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

        paused = false;

        uploading = false;

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

    // =========================================================
    // FORMAT SPEED
    // =========================================================

    private String formatSpeed(
            double bytesPerSecond
    ) {

        if (bytesPerSecond <
                1024) {

            return String.format(
                    "%.0f B/s",
                    bytesPerSecond
            );
        }

        if (bytesPerSecond <
                1024 * 1024) {

            return String.format(
                    "%.1f KB/s",
                    bytesPerSecond
                            / 1024.0
            );
        }

        if (bytesPerSecond <
                1024 * 1024 * 1024) {

            return String.format(
                    "%.1f MB/s",
                    bytesPerSecond
                            / (
                            1024.0
                                    * 1024.0
                    )
            );
        }

        return String.format(
                "%.2f GB/s",
                bytesPerSecond
                        / (
                        1024.0
                                * 1024.0
                                * 1024.0
                )
        );
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

        if (bytes <
                1024L * 1024) {

            return String.format(
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        if (bytes <
                1024L * 1024 * 1024) {

            return String.format(
                    "%.1f MB",
                    bytes / (
                            1024.0
                                    * 1024.0
                    )
            );
        }

        return String.format(
                "%.2f GB",
                bytes / (
                        1024.0
                                * 1024.0
                                * 1024.0
                )
        );
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
            }
