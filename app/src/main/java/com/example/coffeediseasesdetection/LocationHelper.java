package com.example.coffeediseasesdetection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public final class LocationHelper {

    public static final class LatLng {
        public final double latitude;
        public final double longitude;

        public LatLng(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public interface Callback {
        void onResult(LatLng location);
    }

    private LocationHelper() {}

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void fetchLocation(Context context, Callback callback) {
        if (!hasPermission(context)) {
            callback.onResult(null);
            return;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        callback.onResult(new LatLng(loc.getLatitude(), loc.getLongitude()));
                    } else {
                        CancellationTokenSource cts = new CancellationTokenSource();
                        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken())
                                .addOnSuccessListener(l -> callback.onResult(
                                        l != null ? new LatLng(l.getLatitude(), l.getLongitude()) : null))
                                .addOnFailureListener(e -> callback.onResult(null));
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
