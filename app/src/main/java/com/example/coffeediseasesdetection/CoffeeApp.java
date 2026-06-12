package com.example.coffeediseasesdetection;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

import org.osmdroid.config.Configuration;

public class CoffeeApp extends Application {

    private static final String TAG = "CoffeeApp";

    @Override
    protected void attachBaseContext(Context base) {
        try {
            if (FirebaseApp.getApps(base).isEmpty()) {
                FirebaseApp.initializeApp(base);
            }
            initFirebasePersistence(base);
        } catch (Exception e) {
            Log.w(TAG, "Early Firebase init skipped", e);
        }
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
            }
            LocaleHelper.applyAppLocale(this);
            ThemeHelper.applySavedTheme(this);
            initOsmdroid(this);
            initFirebasePersistence(this);
            scheduleBackgroundWork();
        } catch (Exception e) {
            Log.e(TAG, "Application init failed", e);
        }
    }

    private static void initOsmdroid(Context context) {
        try {
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
            Configuration.getInstance().setUserAgentValue(context.getPackageName());
        } catch (Exception e) {
            Log.w(TAG, "OSMDroid init skipped", e);
        }
    }

    private static void initFirebasePersistence(Context context) {
        try {
            FirebaseDatabase.getInstance(AuthHelper.RTDB_URL).setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "RTDB persistence already enabled or unavailable", e);
        }
    }

    /** Defer non-critical work so the launcher activity can start before native ML libs load. */
    private void scheduleBackgroundWork() {
        new Thread(() -> {
            try {
                FcmTokenHelper.refreshAndSave();
            } catch (Exception e) {
                Log.w(TAG, "FCM token refresh skipped", e);
            }
            try {
                NotificationScheduler.scheduleScanReminder(getApplicationContext());
            } catch (Exception e) {
                Log.w(TAG, "Scan reminder schedule skipped", e);
            }
        }, "app-init-bg").start();
    }
}
