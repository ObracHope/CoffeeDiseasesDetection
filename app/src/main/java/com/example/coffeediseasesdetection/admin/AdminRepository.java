package com.example.coffeediseasesdetection.admin;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.coffeediseasesdetection.AuthHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Admin data hub — single place for Firestore/RTDB listeners and computeStats(),
 * matching the web dashboard firebase-data.js pattern.
 */
public final class AdminRepository {

    private static final String PREFS = "admin_data_hub_cache";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    public interface OverviewCallback {
        void onSuccess(AdminOverview overview);

        void onError(Exception e);
    }

    public interface ScansCallback {
        void onSuccess(List<Map<String, Object>> scans);

        void onError(Exception e);
    }

    public interface SimpleCallback {
        void onSuccess();

        void onError(Exception e);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final DatabaseReference rtdb =
            FirebaseDatabase.getInstance(AuthHelper.RTDB_URL).getReference();

    private final List<Map<String, Object>> farmersCache = new ArrayList<>();
    private final List<Map<String, Object>> scansCache = new ArrayList<>();
    private int activityLogsCount;
    private int pendingChallenges;
    private int totalChallenges;
    private boolean scansLoaded;
    private boolean farmersLoaded;
    private boolean logsLoaded;
    private boolean challengesLoaded;

    @Nullable
    private OverviewCallback overviewCallback;
    @Nullable
    private ListenerRegistration usersListener;
    @Nullable
    private ListenerRegistration scansListener;
    @Nullable
    private ListenerRegistration logsListener;
    @Nullable
    private ValueEventListener challengesListener;

    // ── Real-time overview hub ──────────────────────────────────────────────

    public void startOverview(@Nullable Context context, @NonNull OverviewCallback callback) {
        stopOverview();
        overviewCallback = callback;
        farmersLoaded = false;
        scansLoaded = false;
        logsLoaded = false;
        challengesLoaded = false;

        AdminOverview cached = loadCache(context);
        if (cached != null) {
            callback.onSuccess(cached);
        }

        usersListener = firestore.collection("users").addSnapshotListener((snap, error) -> {
            farmersCache.clear();
            if (snap != null) {
                for (QueryDocumentSnapshot doc : snap) {
                    Map<String, Object> row = new HashMap<>(doc.getData());
                    row.put("id", doc.getId());
                    farmersCache.add(row);
                }
            }
            farmersLoaded = true;
            emitOverview(context);
        });

        scansListener = firestore.collection("scan_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(500)
                .addSnapshotListener((snap, error) -> {
                    scansCache.clear();
                    if (snap != null) {
                        for (QueryDocumentSnapshot doc : snap) {
                            Map<String, Object> row = new HashMap<>(doc.getData());
                            row.put("id", doc.getId());
                            row.put("_source", "scan_history");
                            scansCache.add(row);
                        }
                    }
                    if (scansCache.isEmpty()) {
                        bootstrapRtdbScans(context);
                    } else {
                        scansLoaded = true;
                        emitOverview(context);
                    }
                });

        logsListener = firestore.collection("admin_activity_logs")
                .addSnapshotListener((snap, error) -> {
                    activityLogsCount = snap != null ? snap.size() : 0;
                    logsLoaded = true;
                    emitOverview(context);
                });

        challengesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                pendingChallenges = 0;
                totalChallenges = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    totalChallenges++;
                    if ("pending".equals(child.child("status").getValue(String.class))) {
                        pendingChallenges++;
                    }
                }
                challengesLoaded = true;
                emitOverview(context);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                challengesLoaded = true;
                emitOverview(context);
            }
        };
        rtdb.child("farmer_challenges").addValueEventListener(challengesListener);
    }

    public void stopOverview() {
        if (usersListener != null) {
            usersListener.remove();
            usersListener = null;
        }
        if (scansListener != null) {
            scansListener.remove();
            scansListener = null;
        }
        if (logsListener != null) {
            logsListener.remove();
            logsListener = null;
        }
        if (challengesListener != null) {
            rtdb.child("farmer_challenges").removeEventListener(challengesListener);
            challengesListener = null;
        }
        overviewCallback = null;
    }

    private void bootstrapRtdbScans(@Nullable Context context) {
        rtdb.child("scan_history").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!scansCache.isEmpty()) {
                    scansLoaded = true;
                    emitOverview(context);
                    return;
                }
                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, Object> row = snapshotToMap(child);
                    row.put("id", child.getKey());
                    row.put("_source", "rtdb");
                    scansCache.add(row);
                }
                sortScansByDate(scansCache);
                scansLoaded = true;
                emitOverview(context);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                scansLoaded = true;
                emitOverview(context);
            }
        });
    }

    private void emitOverview(@Nullable Context context) {
        if (overviewCallback == null) return;
        if (!farmersLoaded || !scansLoaded || !logsLoaded || !challengesLoaded) return;

        AdminOverview overview = computeStats();
        overview.liveSync = true;
        saveCache(context, overview);
        overviewCallback.onSuccess(overview);
    }

    /** One-shot load (legacy) — delegates to computeStats after fetching. */
    public void loadOverview(@NonNull OverviewCallback callback) {
        final int[] pending = {3};
        final Exception[] error = {null};
        farmersCache.clear();
        scansCache.clear();

        Runnable tryFinish = () -> {
            pending[0]--;
            if (pending[0] <= 0) {
                if (error[0] != null) {
                    callback.onError(error[0]);
                } else {
                    AdminOverview overview = computeStats();
                    callback.onSuccess(overview);
                }
            }
        };

        firestore.collection("users").get()
                .addOnSuccessListener(userSnap -> {
                    for (QueryDocumentSnapshot doc : userSnap) {
                        Map<String, Object> row = new HashMap<>(doc.getData());
                        row.put("id", doc.getId());
                        farmersCache.add(row);
                    }
                    farmersLoaded = true;
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    tryFinish.run();
                });

        firestore.collection("scan_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(500)
                .get()
                .addOnSuccessListener(scanSnap -> {
                    for (QueryDocumentSnapshot doc : scanSnap) {
                        Map<String, Object> row = new HashMap<>(doc.getData());
                        row.put("id", doc.getId());
                        scansCache.add(row);
                    }
                    scansLoaded = true;
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    tryFinish.run();
                });

        firestore.collection("admin_activity_logs").get()
                .addOnSuccessListener(snap -> {
                    activityLogsCount = snap.size();
                    logsLoaded = true;
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    logsLoaded = true;
                    tryFinish.run();
                });
    }

    public AdminOverview computeStats() {
        AdminOverview o = new AdminOverview();

        Calendar startOfDay = Calendar.getInstance();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);
        long dayStartMs = startOfDay.getTimeInMillis();

        Calendar weekAgo = Calendar.getInstance();
        weekAgo.add(Calendar.DAY_OF_YEAR, -7);
        long weekAgoMs = weekAgo.getTimeInMillis();

        Calendar onlineWindow = Calendar.getInstance();
        onlineWindow.add(Calendar.MINUTE, -30);
        long onlineSinceMs = onlineWindow.getTimeInMillis();

        SimpleDateFormat monthFmt = new SimpleDateFormat("MMM yy", Locale.getDefault());
        Map<String, Integer> monthlyCounts = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -i);
            String key = monthFmt.format(c.getTime());
            o.monthLabels.add(key);
            monthlyCounts.put(key, 0);
        }

        int farmers = 0;
        int online = 0;
        Set<String> activeFarmerIds = new HashSet<>();
        Map<String, Integer> diseaseCounts = new HashMap<>();
        Map<String, Integer> areaCounts = new HashMap<>();

        for (Map<String, Object> user : farmersCache) {
            String role = safeString(user.get("role"));
            if (!role.isEmpty() && !"farmer".equalsIgnoreCase(role)) continue;
            farmers++;

            long lastScanMs = parseTimestampMs(user.get("lastScanAt"));
            if (lastScanMs >= onlineSinceMs) online++;

            if (o.recentFarmers.size() < 8) {
                o.recentFarmers.add(user);
            }
        }
        o.totalFarmers = farmers;
        o.activeFarmers = farmers;
        o.onlineUsers = online;

        for (Map<String, Object> scan : scansCache) {
            o.totalScans++;
            if (hasImage(scan)) o.imagesUploaded++;

            long tsMs = parseTimestampMs(scan.get("timestamp"));
            if (tsMs == 0) tsMs = parseTimestampMs(scan.get("createdAtMs"));

            if (tsMs >= dayStartMs) o.todayScans++;

            String uid = safeString(scan.get("userId"));
            if (!uid.isEmpty() && tsMs >= weekAgoMs) activeFarmerIds.add(uid);

            String d = safeString(scan.get("diseaseName"));
            if (d.isEmpty()) d = safeString(scan.get("disease"));

            if (isHealthy(d)) {
                o.healthyCount++;
            } else if (isDisease(d)) {
                o.diseasesDetected++;
                o.infectedCount++;
                diseaseCounts.put(d, diseaseCounts.getOrDefault(d, 0) + 1);
                String area = firstNonEmpty(
                        safeString(scan.get("district")),
                        safeString(scan.get("region")),
                        safeString(scan.get("ward")),
                        "Unknown");
                areaCounts.put(area, areaCounts.getOrDefault(area, 0) + 1);
            }

            if (tsMs > 0) {
                String monthKey = monthFmt.format(new java.util.Date(tsMs));
                if (monthlyCounts.containsKey(monthKey)) {
                    monthlyCounts.put(monthKey, monthlyCounts.get(monthKey) + 1);
                }
            }

            Double lat = parseDouble(scan.get("latitude"));
            Double lng = parseDouble(scan.get("longitude"));
            if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
                o.mapMarkers.add(new double[]{lat, lng});
            }

            if (o.recentScans.size() < 5) {
                o.recentScans.add(scan);
            }
        }

        if (!activeFarmerIds.isEmpty()) {
            o.activeFarmers = activeFarmerIds.size();
        }

        for (String label : o.monthLabels) {
            o.monthCounts.add(monthlyCounts.getOrDefault(label, 0));
        }

        o.topDiseases.addAll(sortEntries(diseaseCounts));
        o.topRegions.addAll(sortEntries(areaCounts));
        o.topDiseasedAreas.clear();
        for (int i = 0; i < Math.min(5, o.topRegions.size()); i++) {
            Map.Entry<String, Integer> e = o.topRegions.get(i);
            o.topDiseasedAreas.add(e.getKey() + " (" + e.getValue() + ")");
        }

        o.activityLogsCount = activityLogsCount;
        o.pendingChallenges = pendingChallenges;
        o.reportsCount = totalChallenges;
        o.farmersOnMap = o.mapMarkers.size();
        o.systemHealth = farmersLoaded && scansLoaded ? "Good" : "Needs attention";

        return o;
    }

    // ── CRUD helpers ────────────────────────────────────────────────────────

    /** Real-time scan listener for admin scan records screen. */
    @NonNull
    public ListenerRegistration listenAllScans(int limit, @NonNull ScansCallback callback) {
        return firestore.collection("scan_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Map<String, Object>> list = new ArrayList<>();
                    if (snap != null) {
                        for (QueryDocumentSnapshot doc : snap) {
                            Map<String, Object> row = new HashMap<>(doc.getData());
                            row.put("id", doc.getId());
                            list.add(row);
                        }
                    }
                    callback.onSuccess(list);
                });
    }

    public void deleteScan(String scanId, @NonNull SimpleCallback callback) {
        firestore.collection("scan_history").document(scanId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
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
                        if (role != null && !"farmer".equalsIgnoreCase(role)) continue;

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

    public void sendBroadcastNotification(String title, String body, Runnable onDone) {
        Map<String, Object> n = new HashMap<>();
        n.put("title", title);
        n.put("body", body);
        n.put("type", "broadcast");
        n.put("audience", "all");
        n.put("createdAtMs", System.currentTimeMillis());
        firestore.collection("notifications").add(n)
                .addOnSuccessListener(ref -> {
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

    // ── Session cache (5 min, like web) ─────────────────────────────────────

    @Nullable
    private AdminOverview loadCache(@Nullable Context context) {
        if (context == null) return null;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long savedAt = p.getLong("savedAt", 0);
        if (System.currentTimeMillis() - savedAt > CACHE_TTL_MS) return null;
        AdminOverview o = new AdminOverview();
        o.totalFarmers = p.getInt("totalFarmers", 0);
        o.totalScans = p.getInt("totalScans", 0);
        o.diseasesDetected = p.getInt("diseasesDetected", 0);
        o.activityLogsCount = p.getInt("activityLogsCount", 0);
        o.todayScans = p.getInt("todayScans", 0);
        o.activeFarmers = p.getInt("activeFarmers", 0);
        o.onlineUsers = p.getInt("onlineUsers", 0);
        o.systemHealth = p.getString("systemHealth", "Good");
        return o;
    }

    private void saveCache(@Nullable Context context, AdminOverview o) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("savedAt", System.currentTimeMillis())
                .putInt("totalFarmers", o.totalFarmers)
                .putInt("totalScans", o.totalScans)
                .putInt("diseasesDetected", o.diseasesDetected)
                .putInt("activityLogsCount", o.activityLogsCount)
                .putInt("todayScans", o.todayScans)
                .putInt("activeFarmers", o.activeFarmers)
                .putInt("onlineUsers", o.onlineUsers)
                .putString("systemHealth", o.systemHealth)
                .apply();
    }

    // ── Utilities ───────────────────────────────────────────────────────────

    private static Map<String, Object> snapshotToMap(DataSnapshot snap) {
        Map<String, Object> map = new HashMap<>();
        for (DataSnapshot child : snap.getChildren()) {
            map.put(child.getKey(), child.getValue());
        }
        return map;
    }

    private static void sortScansByDate(List<Map<String, Object>> scans) {
        scans.sort((a, b) -> Long.compare(
                parseTimestampMs(b.get("timestamp")),
                parseTimestampMs(a.get("timestamp"))));
    }

    private static List<Map.Entry<String, Integer>> sortEntries(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return sorted;
    }

    private static long parseTimestampMs(Object value) {
        if (value == null) return 0;
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Number) return ((Number) value).longValue();
        return 0;
    }

    private static boolean hasImage(Map<String, Object> scan) {
        String url = safeString(scan.get("imageUrl"));
        String path = safeString(scan.get("imagePath"));
        return !url.isEmpty() || !path.isEmpty();
    }

    private static boolean isHealthy(String d) {
        return "Healthy".equalsIgnoreCase(d) || "healthy coffee".equalsIgnoreCase(d);
    }

    private static boolean isDisease(String d) {
        if (d == null || d.isEmpty()) return false;
        String u = d.toUpperCase(Locale.US);
        return !u.startsWith("SORRY") && !isHealthy(d)
                && !"IsNotCoffee".equalsIgnoreCase(d) && !"Uncertain".equalsIgnoreCase(d)
                && !"Error".equalsIgnoreCase(d) && !"Prediction Error".equalsIgnoreCase(d);
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

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "Unknown";
    }
}
