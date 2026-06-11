package com.example.coffeediseasesdetection;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class ScanReminderReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        showScanReminderNotification(context);
        
        // Schedule the next reminder
        NotificationScheduler.scheduleScanReminder(context);
    }
    
    private void showScanReminderNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "scan_reminders",
                    "Scan Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Reminders to scan coffee leaves for disease detection");
            notificationManager.createNotificationChannel(channel);
        }
        
        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "scan_reminders")
                .setSmallIcon(R.drawable.ic_notification_custom)
                .setContentTitle("Coffee Disease Scan Reminder")
                .setContentText("It's time to scan your coffee leaves for early disease detection!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(
                        PendingIntent.getActivity(
                                context,
                                0,
                                new Intent(context, FarmerDashboardActivity.class),
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        )
                );
        
        notificationManager.notify(2002, builder.build());
    }
}
