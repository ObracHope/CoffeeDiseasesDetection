package com.example.coffeediseasesdetection.weather;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.coffeediseasesdetection.weather.WeatherInfo;

/** Updates dashboard weather line every minute and when the dashboard opens (login). */
public final class WeatherBannerHelper {

    public static final int REQUEST_LOCATION = 9207;
    private static final long REFRESH_MS = 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppCompatActivity activity;
    private TextView weatherView;
    private TextView weatherIconView;
    private boolean started;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshNow();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    public void attach(@NonNull AppCompatActivity activity, @Nullable TextView weatherView,
                       @Nullable TextView weatherIconView) {
        this.activity = activity;
        this.weatherView = weatherView;
        this.weatherIconView = weatherIconView;
        if (weatherView == null) return;
        if (weatherIconView != null) weatherIconView.setText("\u2600\uFE0F");
        WeatherInfo fallback = WeatherInfo.fallback();
        weatherView.setText(fallback.formatDetails());
        weatherView.setSelected(true);
        ensurePermissionAndStart();
    }

    public void refresh() {
        refreshNow();
    }

    public void detach() {
        handler.removeCallbacksAndMessages(null);
        started = false;
        activity = null;
        weatherView = null;
        weatherIconView = null;
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull int[] grantResults) {
        if (requestCode != REQUEST_LOCATION || activity == null) return;
        refreshNow();
    }

    private void ensurePermissionAndStart() {
        if (activity == null || weatherView == null) return;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION);
        }
        startRefreshing();
    }

    private void startRefreshing() {
        if (activity == null) return;
        if (!started) {
            started = true;
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, REFRESH_MS);
        }
        refreshNow();
    }

    private void refreshNow() {
        if (activity == null || weatherView == null || activity.isFinishing()) return;
        WeatherService.fetch(activity, info -> {
            Activity act = activity;
            TextView tv = weatherView;
            TextView icon = weatherIconView;
            if (act == null || tv == null || act.isFinishing()) return;
            if (icon != null) icon.setText(info.emoji());
            tv.setText(info.formatDetails());
            tv.setSelected(true);
        });
    }
}
