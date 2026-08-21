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
import android.content.pm.ServiceInfo;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadService extends Service {

    private static final String CHANNEL_ID = "auto_upload";
    private static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_CANCEL =
            "com.example.androidautouploader.CANCEL_UPLOAD";

    private static final String RAILWAY_URL =
            "https://telegram-auto-uploader-production.up.railway.app/upload";

    private static final String VIDEO_PATH =
            "Download/VideoDownloader/%";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean serviceRunning = true;
    private volatile boolean uploading = false;
    private volatile boolean cancelRequested = false;

    private volatile HttpURLConnection currentConnection = null;

    private final Runnable scanner = new Runnable() {

        @Override
        public void run() {

            if (!serviceRunning) {
                return;
            }

            if (!uploading && !executor.isShutdown()) {
                executor.execute(() -> scanAndUpload());
            }

            handler.postDelayed(this, 10000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        startForegroundSafely(
                "Watching VideoDownloader..."
        );

        handler.post(scanner);
    }

    // =========================================================
    // FOREGROUND SERVICE
    // =========================================================

    private void startForegroundSafely(String text) {

        Notification notification =
                createNotification(
                        text,
                        false,
                        0
                );

        if (Build.VERSION.SDK_INT >= 34) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }
    }

    // =========================================================
    // NOTIFICATION
    // =========================================================

    private Notification createNotification(
            String text,
            boolean showProgress,
            int progress
    ) {

        Intent cancelIntent =
                new Intent(
                        this,
                        UploadService.class
                );

        cancelIntent.setAction(ACTION_CANCEL);

        PendingIntent cancelPendingIntent =
                PendingIntent.getService(
                        this,
                        500,
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
                                android.R.drawable.ic_menu_upload
                        )
                        .setContentTitle(
                                "Telegram Auto Uploader"
                        )
                        .setContentText(text)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setPriority(
                                NotificationCompat.PRIORITY_LOW
                        );

        if (showProgress) {

            builder.setProgress(
                    100,
                    Math.max(
                            0,
                            Math.min(100, progress)
                    ),
                    false
            );

            builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "CANCEL",
                    cancelPendingIntent
            );
        }

        return builder.build();
    }

    private void updateNotification(
            String text
    ) {

        if (!serviceRunning) {
            return;
        }

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            manager.notify(
                    NOTIFICATION_ID,
                    createNotification(
                            text,
                            false,
                            0
                    )
            );
        }
    }

    private void updateProgress(
            String filename,
            int progress
    ) {

        if (!serviceRunning) {
            return;
        }

        String name = filename;

        if (name.length() > 30) {

            name =
                    name.substring(0, 27)
                            + "...";
        }

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            manager.notify(
                    NOTIFICATION_ID,
                    createNotification(
                            "Uploading: "
                                    + name
                                    + " • "
                                    + progress
                                    + "%",
                            true,
                            progress
                    )
            );
        }
    }

    private void showSuccess(
            String filename
    ) {

        updateNotification(
                "✅ Upload complete: "
                        + filename
        );

        handler.postDelayed(
                () -> {

                    if (serviceRunning &&
                            !uploading) {

                        updateNotification(
                                "Watching VideoDownloader..."
                        );
                    }

                },
                2000
        );
    }

    private void showFailed(
            String filename
    ) {

        updateNotification(
                "❌ Upload failed: "
                        + filename
        );

        handler.postDelayed(
                () -> {

                    if (serviceRunning &&
                            !uploading) {

                        updateNotification(
                                "Watching VideoDownloader..."
                        );
                    }

                },
                3000
        );
    }

    private void showCancelled() {

        updateNotification(
                "⛔ Upload cancelled"
        );

        handler.postDelayed(
                () -> {

                    if (serviceRunning) {

                        updateNotification(
                                "Watching VideoDownloader..."
                        );
                    }

                },
                1500
        );
    }

    // =========================================================
    // SCAN
    // =========================================================

    private void scanAndUpload() {

        if (!serviceRunning || uploading) {
            return;
        }

        ContentResolver resolver =
                getContentResolver();

        String[] projection = {

                MediaStore.Video.Media._ID,

                MediaStore.Video.Media.DISPLAY_NAME,

                MediaStore.Video.Media.RELATIVE_PATH,

                MediaStore.Video.Media.SIZE
        };

        String selection =
                MediaStore.Video.Media.RELATIVE_PATH
                        + " LIKE ?";

        String[] args = {
                VIDEO_PATH
        };

        Cursor cursor = null;

        try {

            cursor =
                    resolver.query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            args,
                            MediaStore.Video.Media.DATE_ADDED
                                    + " DESC"
                    );

            if (cursor == null) {
                return;
            }

            int idColumn =
                    cursor.getColumnIndex(
                            MediaStore.Video.Media._ID
                    );

            int nameColumn =
                    cursor.getColumnIndex(
                            MediaStore.Video.Media.DISPLAY_NAME
                    );

            int sizeColumn =
                    cursor.getColumnIndex(
                            MediaStore.Video.Media.SIZE
                    );

            if (idColumn < 0 ||
                    nameColumn < 0 ||
                    sizeColumn < 0) {

                return;
            }

            while (
                    cursor.moveToNext()
                            && serviceRunning
            ) {

                long id =
                        cursor.getLong(idColumn);

                String filename =
                        cursor.getString(nameColumn);

                long fileSize =
                        cursor.getLong(sizeColumn);

                Uri uri =
                        ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                        );

                cancelRequested = false;
                uploading = true;

                boolean success =
                        uploadFile(
                                uri,
                                filename,
                                fileSize
                        );

                uploading = false;

                if (!serviceRunning) {
                    return;
                }

                if (cancelRequested) {

                    showCancelled();

                    continue;
                }

                if (success) {

                    try {

                        resolver.delete(
                                uri,
                                null,
                                null
                        );

                    } catch (Exception ignored) {
                    }

                    showSuccess(filename);

                } else {

                    showFailed(filename);
                }
            }

        } catch (Exception e) {

            if (serviceRunning) {

                updateNotification(
                        "Watching VideoDownloader..."
                );
            }

        } finally {

            if (cursor != null) {

                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }

            uploading = false;
        }
    }

    // =========================================================
    // UPLOAD
    // =========================================================

    private boolean uploadFile(
            Uri uri,
            String filename,
            long totalSize
    ) {

        HttpURLConnection connection = null;

        String boundary =
                "----AutoUploaderBoundary";

        try {

            URL url =
                    new URL(RAILWAY_URL);

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            currentConnection = connection;

            connection.setRequestMethod("POST");

            connection.setDoOutput(true);
            connection.setDoInput(true);

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
                            connection.getOutputStream()
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
                    "Content-Type: video/mp4\r\n\r\n"
            );

            ContentResolver resolver =
                    getContentResolver();

            InputStream rawInput =
                    resolver.openInputStream(uri);

            if (rawInput == null) {

                output.close();

                return false;
            }

            try (
                    BufferedInputStream input =
                            new BufferedInputStream(
                                    rawInput
                            )
            ) {

                byte[] buffer =
                        new byte[1024 * 1024];

                long uploadedBytes = 0;

                int bytesRead;

                int lastProgress = -1;

                while (
                        serviceRunning
                                && !cancelRequested
                                && (
                                bytesRead =
                                        input.read(buffer)
                        ) != -1
                ) {

                    output.write(
                            buffer,
                            0,
                            bytesRead
                    );

                    uploadedBytes += bytesRead;

                    int progress = 0;

                    if (totalSize > 0) {

                        progress =
                                (int)
                                        Math.min(
                                                100,
                                                uploadedBytes
                                                        * 100L
                                                        / totalSize
                                        );
                    }

                    if (progress != lastProgress) {

                        lastProgress =
                                progress;

                        final int p =
                                progress;

                        handler.post(
                                () ->
                                        updateProgress(
                                                filename,
                                                p
                                        )
                        );
                    }
                }
            }

            if (
                    !serviceRunning
                            || cancelRequested
            ) {

                try {
                    output.close();
                } catch (Exception ignored) {
                }

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

            int responseCode =
                    connection.getResponseCode();

            return responseCode >= 200
                    && responseCode < 300;

        } catch (Exception e) {

            return false;

        } finally {

            currentConnection = null;

            if (connection != null) {

                try {
                    connection.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // =========================================================
    // CANCEL CURRENT UPLOAD
    // =========================================================

    private void cancelCurrentUpload() {

        if (!uploading) {
            return;
        }

        cancelRequested = true;

        HttpURLConnection connection =
                currentConnection;

        if (connection != null) {

            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }

        showCancelled();

        /*
         * IMPORTANT:
         * Service is NOT stopped here.
         * Watcher continues after cancellation.
         */
    }

    // =========================================================
    // CHANNEL
    // =========================================================

    private void createNotificationChannel() {

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.O
        ) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Telegram Auto Uploader",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Video upload status"
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
    // SERVICE COMMAND
    // =========================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (
                intent != null
                        && ACTION_CANCEL.equals(
                        intent.getAction()
                )
        ) {

            cancelCurrentUpload();

            return START_STICKY;
        }

        return START_STICKY;
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        serviceRunning = false;

        cancelRequested = true;

        handler.removeCallbacks(scanner);

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

    @Nullable
    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
}
