package com.example.androidautouploader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UploadService extends Service {

    private static final String CHANNEL = "auto_upload";

    private static final String RAILWAY_URL =
            "https://telegram-auto-uploader-production.up.railway.app/upload";

    private final Handler handler = new Handler();
    private boolean running = true;

    private final Runnable scanner = new Runnable() {
        @Override
        public void run() {
            if (running) {
                scanAndUpload();
                handler.postDelayed(this, 10000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL)
                        .setContentTitle("Telegram Auto Uploader")
                        .setContentText("Watching VideoDownloader...")
                        .setSmallIcon(android.R.drawable.ic_menu_upload)
                        .setOngoing(true)
                        .build();

        startForeground(1001, notification);

        handler.post(scanner);
    }

    private void scanAndUpload() {

        ContentResolver resolver = getContentResolver();

        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH
        };

        String selection =
                MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";

        String[] args = {
                "Download/VideoDownloader/%"
        };

        try (Cursor cursor = resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
        )) {

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

            while (cursor.moveToNext()) {

                long id = cursor.getLong(idColumn);

                String filename =
                        cursor.getString(nameColumn);

                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                );

                boolean uploaded =
                        uploadFile(uri, filename);

                // DELETE ONLY AFTER SUCCESS
                if (uploaded) {
                    resolver.delete(uri, null, null);
                }
            }

        } catch (Exception ignored) {
        }
    }

    private boolean uploadFile(
            Uri uri,
            String filename
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

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            connection.setConnectTimeout(30000);
            connection.setReadTimeout(3600000);

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
                    "--" + boundary + "\r\n"
            );

            output.writeBytes(
                    "Content-Disposition: form-data; " +
                    "name=\"file\"; filename=\"" +
                    filename + "\"\r\n"
            );

            output.writeBytes(
                    "Content-Type: application/octet-stream\r\n\r\n"
            );

            ContentResolver resolver =
                    getContentResolver();

            try (InputStream input =
                         new BufferedInputStream(
                                 resolver.openInputStream(uri)
                         )) {

                byte[] buffer =
                        new byte[1024 * 1024];

                int bytesRead;

                while ((bytesRead =
                        input.read(buffer)) != -1) {

                    output.write(
                            buffer,
                            0,
                            bytesRead
                    );
                }
            }

            output.writeBytes("\r\n");

            output.writeBytes(
                    "--" + boundary + "--\r\n"
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

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
                            "Telegram Auto Uploader",
                            NotificationManager
                                    .IMPORTANCE_LOW
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

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        running = false;

        handler.removeCallbacks(scanner);

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
