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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadService extends Service {

    private static final String CHANNEL_ID =
            "telegram_auto_uploader";

    private static final int NOTIFICATION_ID =
            1001;

    private static final String ACTION_CANCEL =
            "com.example.androidautouploader.CANCEL_UPLOAD";

    private static final String RAILWAY_URL =
            "https://telegram-auto-uploader-production.up.railway.app/upload";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean running = true;

    private volatile boolean uploading = false;

    private volatile HttpURLConnection currentConnection;

    private final Runnable scanner =
            new Runnable() {

                @Override
                public void run() {

                    if (!running) {
                        return;
                    }

                    if (!uploading &&
                            !executor.isShutdown()) {

                        executor.execute(
                                () -> scanAndUpload()
                        );
                    }

                    if (running) {
                        handler.postDelayed(
                                this,
                                10000
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

        createNotificationChannel();

        try {

            Notification notification =
                    createNotification(
                            "Watching downloaded videos...",
                            0,
                            false
                    );

            /*
             * IMPORTANT:
             * Android 10+ supports foreground service type.
             * Android 14+ requires the correct type.
             */

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
                    "Automatic video upload status"
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
            boolean showProgress
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

    private void showWatching() {

        if (!running) {
            return;
        }

        Notification notification =
                createNotification(
                        "Watching downloaded videos...",
                        0,
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
                        .setContentText(message)
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
    // SCAN
    // =========================================================

    private void scanAndUpload() {

        if (!running || uploading) {
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

        String[] selectionArgs = {
                "Download/VideoDownloader/%"
        };

        Cursor cursor = null;

        try {

            cursor =
                    resolver.query(
                            MediaStore.Video.Media
                                    .EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            MediaStore.Video.Media
                                    .DATE_ADDED
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
                            MediaStore.Video.Media
                                    .DISPLAY_NAME
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
                            && running
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

                Uri uri =
                        ContentUris.withAppendedId(
                                MediaStore.Video.Media
                                        .EXTERNAL_CONTENT_URI,
                                id
                        );

                uploading = true;

                boolean result =
                        uploadFile(
                                uri,
                                filename,
                                totalSize
                        );

                uploading = false;

                if (!running) {
                    return;
                }

                if (result) {

                    try {

                        resolver.delete(
                                uri,
                                null,
                                null
                        );

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    showResult(
                            "✅ Upload complete: "
                                    + filename
                    );

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
    // UPLOAD
    // =========================================================

    private boolean uploadFile(
            Uri uri,
            String filename,
            long totalSize
    ) {

        HttpURLConnection connection = null;

        String boundary =
                "----AutoUploaderBoundary123456";

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
                    "Content-Type: video/mp4\r\n"
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

                    if (progress !=
                            lastProgress) {

                        lastProgress =
                                progress;

                        final int p =
                                progress;

                        handler.post(
                                () ->
                                        updateNotification(
                                                "Uploading: "
                                                        + filename
                                                        + " • "
                                                        + p
                                                        + "%",
                                                p
                                        )
                        );
                    }
                }
            }

            if (!running) {

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
    // CANCEL
    // =========================================================

    private void cancelUpload() {

        running = false;

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
    // START COMMAND
    // =========================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent != null &&
                ACTION_CANCEL.equals(
                        intent.getAction()
                )) {

            cancelUpload();

            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    // =========================================================
    // ANDROID 15+ DATA SYNC TIMEOUT
    // =========================================================

    @Override
    public void onTimeout(
            int startId,
            int fgsType
    ) {

        running = false;

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
