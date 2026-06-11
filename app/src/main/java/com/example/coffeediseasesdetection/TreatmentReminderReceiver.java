package com.example.coffeediseasesdetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TreatmentReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int days = intent != null ? intent.getIntExtra("daysAfter", 7) : 7;
        String title = context.getString(R.string.treatment_reminder_title);
        String body = context.getString(R.string.treatment_reminder_body, days, "");
        NotificationHelper.showNotification(context, title, body);
    }
}
