package com.example.coffeediseasesdetection;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "coffee_disease_notifications";
    private static final String CHANNEL_NAME = "Coffee Disease Alerts";

    public static void showNotification(Context context, String title, String message) {
        // Increment count for dashboard AND save to SQLite inside this method
        FarmerDashboardActivity.incrementNotificationCount(context, title, message);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, NotificationsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_custom)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    public static void showNewUserNotification(Context context, String name, String role) {
        showNotification(context, "New User Registered", "Name: " + name + " (" + role + ")");
    }

    public static void createDiseaseDetectionNotification(Context context, String disease) {
        showNotification(context,
                context.getString(R.string.disease_detected_title),
                context.getString(R.string.notif_scan_message_short, disease));
    }

    public static void createDiseaseDetectionNotification(Context context, String scanId,
                                                            String diseaseName, String confidence,
                                                            String imageUrl, String imagePath) {
        String title = context.getString(R.string.disease_detected_title);
        String message = context.getString(R.string.notif_scan_message, diseaseName, confidence);
        FarmerDashboardActivity.incrementScanNotification(context, title, message, scanId,
                imageUrl, imagePath, diseaseName, confidence);
        showNotification(context, title, message);
    }

    public static void pushToUserIfCurrent(Context context, String targetUserId, String title, String message) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getUid().equals(targetUserId)) {
            showNotification(context, title, message);
        }
    }
}
