package com.example.coffeediseasesdetection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.AlarmManagerCompat;

public class NotificationScheduler {
    
    private static final int SCAN_REMINDER_REQUEST_CODE = 1001;
    private static final long REMINDER_INTERVAL = 14 * 24 * 60 * 60 * 1000L; // 14 days in milliseconds
    
    public static void scheduleScanReminder(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            Intent intent = new Intent(context, ScanReminderReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    SCAN_REMINDER_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            long triggerTime = System.currentTimeMillis() + REMINDER_INTERVAL;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                        alarmManager,
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }
        } catch (SecurityException ignored) {
            // Exact alarms may be denied on some devices — non-fatal.
        }
    }
    
    public static void cancelScanReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ScanReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                SCAN_REMINDER_REQUEST_CODE, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
    }

    /** Expert follow-up: re-scan after 3, 7, and 14 days post-treatment. */
    public static void scheduleTreatmentReminders(Context context, String scanId) {
        int[] days = {3, 7, 14};
        for (int i = 0; i < days.length; i++) {
            scheduleTreatmentAlarm(context, scanId, days[i], 4000 + i);
        }
    }

    private static void scheduleTreatmentAlarm(Context context, String scanId, int daysAfter, int reqCode) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(context, TreatmentReminderReceiver.class);
            intent.putExtra("scanId", scanId);
            intent.putExtra("daysAfter", daysAfter);
            PendingIntent pi = PendingIntent.getBroadcast(context, reqCode + scanId.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long trigger = System.currentTimeMillis() + (daysAfter * 24L * 60 * 60 * 1000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        } catch (SecurityException ignored) {
            // Exact alarms may be denied on some devices — non-fatal.
        }
    }
}
