package com.example.coffeediseasesdetection.admin;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.example.coffeediseasesdetection.AuthHelper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdminRepository {

    public interface OverviewCallback {
        void onSuccess(AdminOverview overview);

        void onError(Exception e);
    }

    public interface ScansCallback {
        void onSuccess(List<Map<String, Object>> scans);

        void onError(Exception e);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final DatabaseReference rtdb =
            FirebaseDatabase.getInstance(AuthHelper.RTDB_URL).getReference();

    public void loadOverview(@NonNull OverviewCallback callback) {
        AdminOverview overview = new AdminOverview();
        final int[] pending = {3};
        final Exception[] error = {null};

        Runnable tryFinish = () -> {
            pending[0]--;
            if (pending[0] <= 0) {
                if (error[0] != null) {
                    callback.onError(error[0]);
                } else {
                    callback.onSuccess(overview);
                }
            }
        };

        Calendar startOfDay = Calendar.getInstance();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);
        Timestamp dayStart = new Timestamp(startOfDay.getTime());

        Calendar activeWindow = Calendar.getInstance();
        activeWindow.add(Calendar.DAY_OF_YEAR, -7);
        Timestamp weekAgo = new Timestamp(activeWindow.getTime());

        Calendar onlineWindow = Calendar.getInstance();
        onlineWindow.add(Calendar.MINUTE, -30);
        Timestamp onlineSince = new Timestamp(onlineWindow.getTime());

        firestore.collection("users").get()
                .addOnSuccessListener(userSnap -> {
                    int farmers = 0;
                    int online = 0;
                    for (QueryDocumentSnapshot doc : userSnap) {
                        String role = doc.getString("role");
                        if (role == null || "farmer".equalsIgnoreCase(role)) {
                            farmers++;
                        }
                        Timestamp lastScan = doc.getTimestamp("lastScanAt");
                        if (lastScan != null && lastScan.compareTo(onlineSince) >= 0) {
                            online++;
                        }
                    }
                    overview.totalFarmers = farmers;
                    overview.activeFarmers = farmers;
                    overview.onlineUsers = online;
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    tryFinish.run();
                });

        firestore.collection("scan_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(300)
                .get()
                .addOnSuccessListener(scanSnap -> {
                    int today = 0;
                    int images = 0;
                    int diseases = 0;
                    Set<String> activeFarmerIds = new HashSet<>();
                    Map<String, Integer> areaCounts = new HashMap<>();

                    for (QueryDocumentSnapshot doc : scanSnap) {
                        images++;
                        overview.totalScans++;

                        Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts != null && ts.compareTo(dayStart) >= 0) {
                            today++;
                        }

                        String uid = doc.getString("userId");
                        if (uid != null && ts != null && ts.compareTo(weekAgo) >= 0) {
                            activeFarmerIds.add(uid);
                        }

                        String d = doc.getString("diseaseName");
                        if (d == null) d = doc.getString("disease");
                        if (isDisease(d)) {
                            diseases++;
                            String area = firstNonEmpty(
                                    doc.getString("district"),
                                    doc.getString("region"),
                                    doc.getString("ward"),
                                    "Unknown");
                            areaCounts.put(area, areaCounts.getOrDefault(area, 0) + 1);
                        }

                        if (overview.recentScans.size() < 8) {
                            Map<String, Object> row = doc.getData();
                            row.put("id", doc.getId());
                            overview.recentScans.add(row);
                        }
                    }

                    overview.todayScans = today;
                    overview.imagesUploaded = images;
                    overview.diseasesDetected = diseases;
                    if (!activeFarmerIds.isEmpty()) {
                        overview.activeFarmers = activeFarmerIds.size();
                    }

                    List<Map.Entry<String, Integer>> sorted = new ArrayList<>(areaCounts.entrySet());
                    sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                    overview.topDiseasedAreas.clear();
                    for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                        Map.Entry<String, Integer> e = sorted.get(i);
                        overview.topDiseasedAreas.add(e.getKey() + " (" + e.getValue() + ")");
                    }

                    overview.systemHealth = "Good";
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    overview.systemHealth = "Limited";
                    error[0] = e;
                    tryFinish.run();
                });

        rtdb.child("farmer_challenges").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int pendingCount = 0;
                int total = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    total++;
                    if ("pending".equals(child.child("status").getValue(String.class))) {
                        pendingCount++;
                    }
                }
                overview.reportsCount = total;
                overview.pendingChallenges = pendingCount;
                tryFinish.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError errorDb) {
                tryFinish.run();
            }
        });
    }

    public void loadAllScans(int limit, @NonNull ScansCallback callback) {
        firestore.collection("scan_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        Map<String, Object> row = doc.getData();
                        row.put("id", doc.getId());
                        list.add(row);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    public void loadScansWithImages(int limit, @NonNull ScansCallback callback) {
        firestore.collection("scan_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String url = doc.getString("imageUrl");
                        String path = doc.getString("imagePath");
                        boolean hasUrl = url != null && !url.trim().isEmpty();
                        boolean hasPath = path != null && !path.trim().isEmpty();
                        if (!hasUrl && !hasPath) continue;
                        Map<String, Object> row = doc.getData();
                        row.put("id", doc.getId());
                        list.add(row);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    public void loadFarmersWithLocation(@NonNull ScansCallback callback) {
        firestore.collection("users").get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String role = doc.getString("role");
                        if (role != null && !"farmer".equalsIgnoreCase(role)) {
                            continue;
                        }
                        String region = safeString(doc.get("region"));
                        String district = safeString(doc.get("district"));
                        String ward = safeString(doc.get("ward"));
                        Double lat = parseDouble(doc.get("lastLatitude"));
                        Double lng = parseDouble(doc.get("lastLongitude"));
                        boolean hasGps = lat != null && lng != null && !(lat == 0.0 && lng == 0.0);
                        boolean hasRegion = !region.isEmpty() || !district.isEmpty() || !ward.isEmpty();
                        if (!hasGps && !hasRegion) continue;

                        Map<String, Object> row = doc.getData();
                        row.put("id", doc.getId());
                        row.put("hasGps", hasGps);
                        list.add(row);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    private static String safeString(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    public static Double parseDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void replyToChallenge(String challengeId, String reply, Runnable onDone) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("adminReply", reply);
        updates.put("adminReplyAt", System.currentTimeMillis());
        updates.put("status", "resolved");
        rtdb.child("farmer_challenges").child(challengeId).updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    if (onDone != null) onDone.run();
                });
    }

    public void logActivity(Context context, String action, String detail) {
        Map<String, Object> log = new HashMap<>();
        log.put("action", action);
        log.put("detail", detail);
        log.put("timestamp", Timestamp.now());
        log.put("adminUid", com.google.firebase.auth.FirebaseAuth.getInstance().getUid());
        firestore.collection("admin_activity_logs").add(log);
    }

    public void loadActivityLogs(@NonNull ScansCallback callback) {
        firestore.collection("admin_activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        Map<String, Object> row = doc.getData();
                        row.put("id", doc.getId());
                        list.add(row);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    private static boolean isDisease(String d) {
        if (d == null) return false;
        return !d.startsWith("SORRY") && !d.equals("Healthy")
                && !d.equals("IsNotCoffee") && !d.equals("Uncertain")
                && !d.equals("Error");
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "Unknown";
    }
}
