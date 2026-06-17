package com.example.coffeediseasesdetection.admin;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.coffeediseasesdetection.ActivityLogHelper;
import com.example.coffeediseasesdetection.AuthHelper;
import com.google.android.gms.tasks.Tasks;
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
    private final List<Map<String, Object>> farmersCacheFs = new ArrayList<>();
    private final List<Map<String, Object>> farmersCacheRtdb = new ArrayList<>();
    private final List<Map<String, Object>> scansCache = new ArrayList<>();
    private final List<Map<String, Object>> scansCacheDetection = new ArrayList<>();
    private int activityLogsCount;
    private int pendingChallenges;
    private int totalChallenges;
    private boolean scansLoaded;
    private boolean scansDetectionLoaded;
    private boolean farmersLoaded;
    private boolean farmersFsLoaded;
    private boolean farmersRtdbLoaded;
    private boolean logsLoaded;
    private boolean challengesLoaded;

    @Nullable
    private OverviewCallback overviewCallback;
    @Nullable
    private ListenerRegistration usersListener;
    @Nullable
    private ListenerRegistration scansListener;
    @Nullable
    private ListenerRegistration detectionScansListener;
    @Nullable
    private ListenerRegistration logsListener;
    @Nullable
    private ValueEventListener challengesListener;
    @Nullable
    private ValueEventListener rtdbUsersListener;

    // ── Real-time overview hub ──────────────────────────────────────────────

    public void startOverview(@Nullable Context context, @NonNull OverviewCallback callback) {
        stopOverview();
        overviewCallback = callback;
        farmersLoaded = false;
        farmersFsLoaded = false;
        farmersRtdbLoaded = false;
        scansLoaded = false;
        scansDetectionLoaded = false;
        logsLoaded = false;
        challengesLoaded = false;

        AdminOverview cached = loadCache(context);
        if (cached != null) {
            callback.onSuccess(cached);
        }

        usersListener = firestore.collection("users").addSnapshotListener((snap, error) -> {
            farmersCacheFs.clear();
            if (snap != null) {
                for (QueryDocumentSnapshot doc : snap) {
                    Map<String, Object> row = new HashMap<>(doc.getData());
                    row.put("id", doc.getId());
                    if (Boolean.TRUE.equals(row.get("deleted"))) continue;
                    farmersCacheFs.add(row);
                }
            }
            farmersFsLoaded = true;
            mergeFarmersAndEmit(context);
        });

        rtdbUsersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                farmersCacheRtdb.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, Object> row = snapshotToMap(child);
                    row.put("id", child.getKey());
                    if (Boolean.TRUE.equals(row.get("deleted"))) continue;
                    farmersCacheRtdb.add(row);
                }
                farmersRtdbLoaded = true;
                mergeFarmersAndEmit(context);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                farmersRtdbLoaded = true;
                mergeFarmersAndEmit(context);
            }
        };
        rtdb.child("users").addValueEventListener(rtdbUsersListener);

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
                    scansLoaded = true;
                    mergeScansAndEmit(context);
                });

        detectionScansListener = firestore.collection("detection_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(500)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        firestore.collection("detection_history")
                                .limit(500)
                                .get()
                                .addOnSuccessListener(fallback -> {
                                    scansCacheDetection.clear();
                                    for (QueryDocumentSnapshot doc : fallback) {
                                        Map<String, Object> row = new HashMap<>(doc.getData());
                                        row.put("id", doc.getId());
                                        row.put("_source", "detection_history");
                                        scansCacheDetection.add(row);
                                    }
                                    scansDetectionLoaded = true;
                                    mergeScansAndEmit(context);
                                })
                                .addOnFailureListener(e -> {
                                    scansDetectionLoaded = true;
                                    mergeScansAndEmit(context);
                                });
                        return;
                    }
                    scansCacheDetection.clear();
                    if (snap != null) {
                        for (QueryDocumentSnapshot doc : snap) {
                            Map<String, Object> row = new HashMap<>(doc.getData());
                            row.put("id", doc.getId());
                            row.put("_source", "detection_history");
                            scansCacheDetection.add(row);
                        }
                    }
                    scansDetectionLoaded = true;
                    mergeScansAndEmit(context);
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
        if (rtdbUsersListener != null) {
            rtdb.child("users").removeEventListener(rtdbUsersListener);
            rtdbUsersListener = null;
        }
        if (scansListener != null) {
            scansListener.remove();
            scansListener = null;
        }
        if (detectionScansListener != null) {
            detectionScansListener.remove();
            detectionScansListener = null;
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

    private void mergeFarmersAndEmit(@Nullable Context context) {
        if (!farmersFsLoaded || !farmersRtdbLoaded) return;
        farmersCache.clear();
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> user : farmersCacheRtdb) {
            String id = safeString(user.get("id"));
            if (id.isEmpty()) continue;
            merged.put(id, new HashMap<>(user));
        }
        for (Map<String, Object> user : farmersCacheFs) {
            String id = safeString(user.get("id"));
            if (id.isEmpty()) continue;
            Map<String, Object> prev = merged.get(id);
            if (prev != null) {
                prev.putAll(user);
            } else {
                merged.put(id, new HashMap<>(user));
            }
        }
        farmersCache.addAll(merged.values());
        farmersLoaded = true;
        emitOverview(context);
    }

    private final List<Map<String, Object>> mergedScansCache = new ArrayList<>();

    private void mergeScansAndEmit(@Nullable Context context) {
        if (!scansLoaded || !scansDetectionLoaded) return;
        mergedScansCache.clear();
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> scan : scansCache) {
            deduped.put(scanDedupeKey(scan), new HashMap<>(scan));
        }
        for (Map<String, Object> scan : scansCacheDetection) {
            String key = scanDedupeKey(scan);
            Map<String, Object> prev = deduped.get(key);
            if (prev == null || parseTimestampMs(scan.get("timestamp")) >= parseTimestampMs(prev.get("timestamp"))) {
                deduped.put(key, new HashMap<>(scan));
            }
        }
        mergedScansCache.addAll(deduped.values());
        sortScansByDate(mergedScansCache);
        if (mergedScansCache.isEmpty()) {
            bootstrapRtdbScans(context);
            return;
        }
        emitOverview(context);
    }

    private static String scanDedupeKey(Map<String, Object> scan) {
        long ts = parseTimestampMs(scan.get("timestamp"));
        if (ts == 0) ts = parseTimestampMs(scan.get("createdAtMs"));
        long bucket = ts > 0 ? ts / 60000L : 0L;
        String who = firstNonEmpty(
                safeString(scan.get("userId")),
                safeString(scan.get("userEmail")),
                safeString(scan.get("userName")),
                safeString(scan.get("id")));
        String disease = firstNonEmpty(
                safeString(scan.get("diseaseName")),
                safeString(scan.get("disease")));
        return who + "|" + bucket + "|" + disease;
    }

    private void bootstrapRtdbScans(@Nullable Context context) {
        rtdb.child("scan_history").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mergedScansCache.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, Object> row = snapshotToMap(child);
                    row.put("id", child.getKey());
                    row.put("_source", "rtdb:scan_history");
                    mergedScansCache.add(row);
                }
                sortScansByDate(mergedScansCache);
                emitOverview(context);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                emitOverview(context);
            }
        });
    }

    private void emitOverview(@Nullable Context context) {
        if (overviewCallback == null) return;
        if (!farmersLoaded || !scansLoaded || !scansDetectionLoaded || !logsLoaded || !challengesLoaded) return;

        AdminOverview overview = computeStats();
        overview.liveSync = true;
        saveCache(context, overview);
        overviewCallback.onSuccess(overview);
    }

    /** One-shot load (legacy) — delegates to computeStats after fetching. */
    public void loadOverview(@NonNull OverviewCallback callback) {
        final int[] pending = {4};
        final Exception[] error = {null};
        farmersCache.clear();
        scansCache.clear();
        scansCacheDetection.clear();
        mergedScansCache.clear();

        Runnable tryFinish = () -> {
            pending[0]--;
            if (pending[0] <= 0) {
                if (error[0] != null) {
                    callback.onError(error[0]);
                } else {
                    mergeScansForLoad();
                    farmersLoaded = true;
                    scansLoaded = true;
                    scansDetectionLoaded = true;
                    logsLoaded = true;
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
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    tryFinish.run();
                });

        firestore.collection("detection_history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(500)
                .get()
                .addOnSuccessListener(scanSnap -> {
                    for (QueryDocumentSnapshot doc : scanSnap) {
                        Map<String, Object> row = new HashMap<>(doc.getData());
                        row.put("id", doc.getId());
                        scansCacheDetection.add(row);
                    }
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    tryFinish.run();
                });

        firestore.collection("admin_activity_logs").get()
                .addOnSuccessListener(snap -> {
                    activityLogsCount = snap.size();
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    tryFinish.run();
                });
    }

    private void mergeScansForLoad() {
        mergedScansCache.clear();
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> scan : scansCache) {
            deduped.put(scanDedupeKey(scan), new HashMap<>(scan));
        }
        for (Map<String, Object> scan : scansCacheDetection) {
            String key = scanDedupeKey(scan);
            Map<String, Object> prev = deduped.get(key);
            if (prev == null || parseTimestampMs(scan.get("timestamp")) >= parseTimestampMs(prev.get("timestamp"))) {
                deduped.put(key, new HashMap<>(scan));
            }
        }
        mergedScansCache.addAll(deduped.values());
        sortScansByDate(mergedScansCache);
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
        int activeUsers = 0;
        int inactiveUsers = 0;
        Set<String> activeFarmerIds = new HashSet<>();
        Map<String, Integer> diseaseCounts = new HashMap<>();
        Map<String, Integer> areaCounts = new HashMap<>();

        Calendar activeSince = Calendar.getInstance();
        activeSince.add(Calendar.DAY_OF_YEAR, -7);
        long activeSinceMs = activeSince.getTimeInMillis();

        o.totalUsers = farmersCache.size();

        Map<String, String> userRoles = new HashMap<>();
        for (Map<String, Object> user : farmersCache) {
            String uid = safeString(user.get("id"));
            String role = AuthHelper.normalizeRole(safeString(user.get("role")));
            if (!uid.isEmpty()) userRoles.put(uid, role);
            String email = safeString(user.get("email"));
            if (!email.isEmpty()) userRoles.put(email, role);
        }

        for (Map<String, Object> user : farmersCache) {
            long lastSeenMs = parseTimestampMs(user.get("lastSeenAt"));
            if (lastSeenMs == 0) lastSeenMs = parseTimestampMs(user.get("lastScanAt"));
            if (lastSeenMs >= onlineSinceMs) online++;
            if (lastSeenMs >= activeSinceMs) activeUsers++;
            else inactiveUsers++;

            String role = safeString(user.get("role"));
            if (!role.isEmpty() && !"farmer".equalsIgnoreCase(role)) continue;

            if (o.recentFarmers.size() < 8) {
                o.recentFarmers.add(user);
            }
            farmers++;
        }
        o.totalFarmers = farmers;
        o.onlineUsers = online;
        o.activeUsers = activeUsers;
        o.inactiveUsers = inactiveUsers;

        List<Map<String, Object>> scansForStats = mergedScansCache.isEmpty() ? scansCache : mergedScansCache;
        for (Map<String, Object> scan : scansForStats) {
            o.totalScans++;
            if (hasImage(scan)) o.imagesUploaded++;

            long tsMs = parseTimestampMs(scan.get("timestamp"));
            if (tsMs == 0) tsMs = parseTimestampMs(scan.get("createdAtMs"));

            if (tsMs >= dayStartMs) o.todayScans++;

            String scanSource = safeString(scan.get("scanSource"));
            if (scanSource.isEmpty()) scanSource = safeString(scan.get("scanType"));
            boolean isUpload = "upload".equalsIgnoreCase(scanSource) || "gallery".equalsIgnoreCase(scanSource);
            if (isUpload) {
                o.uploadScans++;
                if (tsMs >= dayStartMs) o.todayUploadScans++;
            } else {
                o.cameraScans++;
                if (tsMs >= dayStartMs) o.todayCameraScans++;
            }

            String uid = safeString(scan.get("userId"));
            if (!uid.isEmpty() && tsMs >= weekAgoMs) activeFarmerIds.add(uid);

            String scanRole = userRoles.get(uid);
            if (scanRole == null) {
                scanRole = userRoles.get(safeString(scan.get("userEmail")));
            }
            if (scanRole == null) scanRole = "farmer";
            o.scansByRole.put(scanRole, o.scansByRole.getOrDefault(scanRole, 0) + 1);
            if (isUpload) {
                o.uploadByRole.put(scanRole, o.uploadByRole.getOrDefault(scanRole, 0) + 1);
            } else {
                o.cameraByRole.put(scanRole, o.cameraByRole.getOrDefault(scanRole, 0) + 1);
            }
            if (AuthHelper.isStaffRole(scanRole)) o.staffScans++;
            else o.farmerScans++;

            String d = safeString(scan.get("diseaseName"));
            if (d.isEmpty()) d = safeString(scan.get("disease"));

            boolean notCoffee = Boolean.TRUE.equals(scan.get("notCoffee"))
                    || "IsNotCoffee".equalsIgnoreCase(d);
            boolean healthCoffee = Boolean.TRUE.equals(scan.get("healthCoffee"))
                    || (isHealthy(d) && !notCoffee);

            if (healthCoffee) {
                o.healthyCount++;
                o.healthCoffeeCount++;
                if (tsMs >= dayStartMs) o.todayHealthy++;
            } else if (notCoffee) {
                o.notCoffeeCount++;
                if (tsMs >= dayStartMs) o.todayNotCoffee++;
            } else if (isDisease(d)) {
                o.diseasesDetected++;
                o.infectedCount++;
                if (tsMs >= dayStartMs) o.todayDiseases++;
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
        ActivityLogHelper.log(context, action, detail);
    }

    public void loadActivityLogs(@NonNull ScansCallback callback) {
        Tasks.whenAllSuccess(
                firestore.collection("admin_activity_logs")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(150)
                        .get(),
                firestore.collection("scan_activity_logs")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(150)
                        .get()
        ).addOnSuccessListener(results -> {
            List<Map<String, Object>> list = mergeActivityLogSnapshots(results);
            callback.onSuccess(list);
        }).addOnFailureListener(e ->
                firestore.collection("admin_activity_logs")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(150)
                        .get()
                        .addOnSuccessListener(snap -> {
                            List<Map<String, Object>> list = new ArrayList<>();
                            for (QueryDocumentSnapshot doc : snap) {
                                Map<String, Object> row = new HashMap<>(doc.getData());
                                row.put("id", doc.getId());
                                list.add(normalizeActivityLogRow(row));
                            }
                            sortActivityLogs(list);
                            callback.onSuccess(list);
                        })
                        .addOnFailureListener(callback::onError));
    }

    private static List<Map<String, Object>> mergeActivityLogSnapshots(List<?> results) {
        List<Map<String, Object>> list = new ArrayList<>();
        Set<String> adminScanIds = new HashSet<>();
        if (results.size() > 0 && results.get(0) instanceof com.google.firebase.firestore.QuerySnapshot) {
            com.google.firebase.firestore.QuerySnapshot adminSnap =
                    (com.google.firebase.firestore.QuerySnapshot) results.get(0);
            for (QueryDocumentSnapshot doc : adminSnap) {
                Map<String, Object> row = new HashMap<>(doc.getData());
                row.put("id", doc.getId());
                row.put("_source", "admin_activity_logs");
                normalizeActivityLogRow(row);
                Object scanId = row.get("scanId");
                String action = String.valueOf(row.get("action"));
                if (scanId != null && (action.contains("scan") || action.contains("upload"))) {
                    adminScanIds.add(String.valueOf(scanId));
                }
                list.add(row);
            }
        }
        if (results.size() > 1 && results.get(1) instanceof com.google.firebase.firestore.QuerySnapshot) {
            com.google.firebase.firestore.QuerySnapshot scanSnap =
                    (com.google.firebase.firestore.QuerySnapshot) results.get(1);
            for (QueryDocumentSnapshot doc : scanSnap) {
                String scanId = doc.getString("scanId");
                if (scanId != null && adminScanIds.contains(scanId)) continue;
                Map<String, Object> row = new HashMap<>(doc.getData());
                row.put("id", doc.getId());
                row.put("_source", "scan_activity_logs");
                if (row.get("action") == null) row.put("action", "scan_completed");
                normalizeActivityLogRow(row);
                list.add(row);
            }
        }
        sortActivityLogs(list);
        if (list.size() > 200) {
            return new ArrayList<>(list.subList(0, 200));
        }
        return list;
    }

    private static Map<String, Object> normalizeActivityLogRow(Map<String, Object> row) {
        if (!row.containsKey("createdAtMs") || row.get("createdAtMs") == null) {
            row.put("createdAtMs", parseTimestampMs(row.get("timestamp")));
        }
        if (row.get("adminName") == null) {
            Object name = row.get("userEmail");
            if (name == null) name = row.get("userName");
            if (name != null) row.put("adminName", name);
        }
        if (row.get("details") == null && row.get("detail") != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("summary", row.get("detail"));
            row.put("details", details);
        }
        if (row.get("details") == null && row.get("diseaseName") != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("disease", row.get("diseaseName"));
            details.put("confidence", row.get("confidence"));
            details.put("scanSource", row.get("scanSource"));
            details.put("region", row.get("region"));
            row.put("details", details);
        }
        return row;
    }

    private static void sortActivityLogs(List<Map<String, Object>> list) {
        Collections.sort(list, (a, b) -> Long.compare(
                parseTimestampMs(b.get("createdAtMs") != null ? b.get("createdAtMs") : b.get("timestamp")),
                parseTimestampMs(a.get("createdAtMs") != null ? a.get("createdAtMs") : a.get("timestamp"))));
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
        o.totalUsers = p.getInt("totalUsers", 0);
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
                .putInt("totalUsers", o.totalUsers)
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
