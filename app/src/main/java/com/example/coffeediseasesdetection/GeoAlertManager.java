package com.example.coffeediseasesdetection;

import android.content.Context;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GeoAlertManager {

    public static final int[] ALERT_RADIUS_METERS = {50, 100, 200};

    private GeoAlertManager() {}

    public static void notifyNearbyFarmers(Context context, FirebaseFirestore db, String sourceUserId,
                                           String scanId, String diseaseKey, double lat, double lng) {
        if (!DiseaseLabels.isDiseaseFound(diseaseKey) || (lat == 0 && lng == 0)) return;

        db.collection("users").limit(500).get().addOnSuccessListener(snap -> {
            Set<String> notified = new HashSet<>();
            for (QueryDocumentSnapshot doc : snap) {
                String uid = doc.getId();
                if (uid.equals(sourceUserId) || notified.contains(uid)) continue;
                String role = doc.getString("role");
                if (role != null && role.toLowerCase().contains("admin")) continue;

                Double uLat = doc.getDouble("lastLatitude");
                Double uLng = doc.getDouble("lastLongitude");
                if (uLat == null || uLng == null) continue;

                double dist = LocationHelper.distanceMeters(lat, lng, uLat, uLng);
                int radius = -1;
                for (int r : ALERT_RADIUS_METERS) {
                    if (dist <= r) { radius = r; break; }
                }
                if (radius < 0) continue;

                String title = context.getString(R.string.geo_alert_title);
                String body = context.getString(R.string.geo_alert_body, radius,
                        DiseaseTextProvider.displayName(context, diseaseKey));

                Map<String, Object> n = new HashMap<>();
                n.put("targetUserId", uid);
                n.put("title", title);
                n.put("body", body);
                n.put("type", "geo_alert");
                n.put("scanId", scanId);
                n.put("disease", diseaseKey);
                n.put("radiusMeters", radius);
                n.put("distanceMeters", Math.round(dist));
                n.put("read", false);
                n.put("createdAtMs", System.currentTimeMillis());
                n.put("timestamp", FieldValue.serverTimestamp());
                db.collection("user_notifications").add(n);

                NotificationHelper.pushToUserIfCurrent(context, uid, title, body);
                notified.add(uid);
            }
        });
    }
}
