package com.example.coffeediseasesdetection;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Map;

/**
 * Activity-level real-time scan listener so farmer dashboard stats update
 * even when HomeFragment is recreated or user returns from a scan.
 */
public interface FarmerScanStatsHost {

    interface StatsCallback {
        void onStatsUpdated(ScanHistoryLoader.DashboardStats stats);
    }

    void setScanStatsCallback(StatsCallback callback);

    void startScanStatsListener();

    void stopScanStatsListener();
}
