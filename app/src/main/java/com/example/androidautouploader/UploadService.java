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

    private static final String CHANNEL = "auto_upload";

    private static final int NOTIFICATION_ID = 1001;

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

    private volatile HttpURLConnection currentConnection = null;

    private final Runnable scanner = new Runnable() {

        @Override
        public void run() {

            if (!running) {
                return;
            }

            if (!uploading) {
                executor.execute(() -> scanAndUpload());
            }

            handler.postDelayed(this, 10000);
        }
    };

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        showWatchingNotification();

        handler.post(scanner);
    }

    // ---------------------------------------------------------
    // NOTIFICATION
    // ---------------------------------------------------------

    private void showWatchingNotification() {

        Notification notification =
                buildNotification(
                        "Watching VideoDownloader...",
                        0,
                        false,
                        false
                );

        startForeground(
                NOTIFICATION_ID,
                notification
        );
    }

    private Notification buildNotification(
            String text,
            int progress,
            boolean showProgress,
            boolean uploadingNow
    ) {

        Intent cancelIntent =
                new Intent(this, UploadService.class);

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
                        CHANNEL
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
                    Math.max(0, Math.min(100, progress)),
                    false
            );

            builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "CANCEL",
                    cancelPendingIntent
            );

        } else if (!uploadingNow) {

            builder.setProgress(
                    0,
                    0,
                    false
            );
        }

        return builder.build();
    }

    private void updateProgress(
            String filename,
            int progress
    ) {

        if (!running) {
            return;
        }

        String shortName = filename;

        if (shortName.length() > 35) {
            shortName =
                    shortName.substring(
                            0,
                            32
                    ) + "...";
        }

        Notification notification =
                buildNotification(
                        "Uploading: "
                                + shortName
                                + " • "
                                + progress
                                + "%",
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

    private void showSuccess(
            String filename
    ) {

        if (!running) {
            return;
        }

        Notification notification =
                buildSimpleNotification(
                        "✅ Upload complete: "
                                + filename
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

    private void showFailed(
            String filename
    ) {

        if (!running) {
            return;
        }

        Notification notification =
                buildSimpleNotification(
                        "❌ Upload failed: "
                                + filename
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

    private void showCancelled() {

        Notification notification =
                buildSimpleNotification(
                        "⛔ Upload cancelled"
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

    private Notification buildSimpleNotification(
            String text
    ) {

        return new NotificationCompat.Builder(
                this,
                CHANNEL
        )
                .setSmallIcon(
                        android.R.drawable.ic_menu_upload
                )
                .setContentTitle(
                        "Telegram Auto Uploader"
                )
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setPriority(
                        NotificationCompat.PRIORITY_LOW
                )
                .build();
    }

    // ---------------------------------------------------------
    // SCAN VIDEO DOWNLOADER
    // ---------------------------------------------------------

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

        String[] args = {
                "Download/VideoDownloader/%"
        };

        try (
                Cursor cursor =
                        resolver.query(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                projection,
                                selection,
                                args,
                                MediaStore.Video.Media.DATE_ADDED
                                        + " DESC"
                        )
        ) {

            if (cursor == null) {
                return;
            }

            int idColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media._ID
                    );

            int nameColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.DISPLAY_NAME
                    );

            int sizeColumn =
                    cursor.getColumnIndexOrThrow(
                            MediaStore.Video.Media.SIZE
                    );

            while (
                    cursor.moveToNext()
                            && running
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

                uploading = true;

                boolean uploaded =
                        uploadFile(
                                uri,
                                filename,
                                fileSize
                        );

                uploading = false;

                if (!running) {
                    return;
                }

                if (uploaded) {

                    try {

                        resolver.delete(
                                uri,
                                null,
                                null
                        );

                    } catch (Exception ignored) {
                    }

                    showSuccess(filename);

                    // Give notification time to show
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                    }

                    if (running) {
                        showWatchingNotification();
                    }

                } else {

                    if (running) {

                        showFailed(filename);

                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException ignored) {
                        }

                        if (running) {
                            showWatchingNotification();
                        }
                    }
                }
            }

        } catch (Exception ignored) {

            if (running) {
                showWatchingNotification();
            }
        }
    }

    // ---------------------------------------------------------
    // UPLOAD
    // ---------------------------------------------------------

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

            InputStream input =
                    resolver.openInputStream(uri);

            if (input == null) {

                output.close();

                return false;
            }

            try (
                    BufferedInputStream bufferedInput =
                            new BufferedInputStream(
                                    input
                            )
            ) {

                byte[] buffer =
                        new byte[1024 * 1024];

                long uploadedBytes = 0;

                int bytesRead;

                int lastProgress = -1;

                while (
                        running
                                && (
                                bytesRead =
                                        bufferedInput.read(
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

                    if (
                            progress != lastProgress
                    ) {

                        lastProgress =
                                progress;

                        final int finalProgress =
                                progress;

                        handler.post(
                                () ->
                                        updateProgress(
                                                filename,
                                                finalProgress
                                        )
                        );
                    }
                }
            }

            if (!running) {

                output.close();

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

                connection.disconnect();
            }
        }
    }

    // ---------------------------------------------------------
    // CANCEL
    // ---------------------------------------------------------

    private void cancelUpload() {

        running = false;

        uploading = false;

        HttpURLConnection connection =
                currentConnection;

        if (connection != null) {

            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }

        handler.removeCallbacks(scanner);

        executor.shutdownNow();

        showCancelled();

        stopForeground(
                STOP_FOREGROUND_REMOVE
        );

        stopSelf();
    }

    // ---------------------------------------------------------
    // NOTIFICATION CHANNEL
    // ---------------------------------------------------------

    private void createNotificationChannel() {

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.O
        ) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
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

    // ---------------------------------------------------------
    // SERVICE
    // ---------------------------------------------------------

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

            cancelUpload();

            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        running = false;

        uploading = false;

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
