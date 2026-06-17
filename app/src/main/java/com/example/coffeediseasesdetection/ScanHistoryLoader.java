package com.example.coffeediseasesdetection;

import android.content.Context;
import android.util.Log;

import com.example.coffeediseasesdetection.data.LocalScanStore;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads scans without composite-index queries.
 * Sources: local SQLite → users/{uid}/scans → scan_history (unordered).
 */
public final class ScanHistoryLoader {

    private static final String TAG = "ScanHistoryLoader";

    public interface Callback {
        void onLoaded(List<Map<String, Object>> scans);
        void onError(Exception e);
    }

    private ScanHistoryLoader() {}

    public static ListenerRegistration listen(Context context, FirebaseUser user, Callback callback) {
        if (user == null) {
            if (callback != null) callback.onError(new IllegalStateException("Not logged in"));
            return null;
        }

        String uid = user.getUid();
        Context app = context != null ? context.getApplicationContext() : null;

        if (app != null && callback != null) {
            List<Map<String, Object>> local = LocalScanStore.loadAll(app, uid);
            if (!local.isEmpty()) {
                callback.onLoaded(local);
            }
        }

        Query query = FirebaseFirestore.getInstance()
                .collection("scan_history")
                .whereEqualTo("userId", uid);

        return query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                Log.w(TAG, "scan_history listener failed, using fallbacks", error);
                loadAllSources(app, uid, callback);
                return;
            }
            List<Map<String, Object>> cloud = toRows(snapshots);
            sortByTimestampDesc(cloud);
            deliver(app, uid, cloud, callback);
        });
    }

    public static ListenerRegistration listen(FirebaseUser user, Callback callback) {
        return listen(null, user, callback);
    }

    public static void loadOnce(Context context, FirebaseUser user, Callback callback) {
        if (user == null) {
            if (callback != null) callback.onError(new IllegalStateException("Not logged in"));
            return;
        }
        loadAllSources(context != null ? context.getApplicationContext() : null, user.getUid(), callback);
    }

    public static void loadOnce(FirebaseUser user, Callback callback) {
        loadOnce(null, user, callback);
    }

    private static void loadAllSources(Context app, String uid, Callback callback) {
        List<Map<String, Object>> merged = new ArrayList<>();
        if (app != null) {
            merged.addAll(LocalScanStore.loadAll(app, uid));
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).collection("scans")
                .get()
                .addOnSuccessListener(userScans -> {
                    merged.addAll(toRows(userScans));
                    db.collection("scan_history")
                            .whereEqualTo("userId", uid)
                            .get()
                            .addOnSuccessListener(historySnap -> {
                                merged.addAll(toRows(historySnap));
                                finishLoad(app, uid, merged, callback, null);
                            })
                            .addOnFailureListener(e -> finishLoad(app, uid, merged, callback, e));
                })
                .addOnFailureListener(e -> db.collection("scan_history")
                        .whereEqualTo("userId", uid)
                        .get()
                        .addOnSuccessListener(historySnap -> {
                            merged.addAll(toRows(historySnap));
                            finishLoad(app, uid, merged, callback, null);
                        })
                        .addOnFailureListener(e2 -> finishLoad(app, uid, merged, callback, e2)));
    }

    private static void deliver(Context app, String uid, List<Map<String, Object>> cloud,
                                Callback callback) {
        if (callback == null) return;
        List<Map<String, Object>> merged = new ArrayList<>(cloud);
        if (app != null) {
            merged.addAll(LocalScanStore.loadAll(app, uid));
        }
        List<Map<String, Object>> deduped = dedupeById(merged);
        sortByTimestampDesc(deduped);
        callback.onLoaded(deduped);
    }

    private static void finishLoad(Context app, String uid, List<Map<String, Object>> merged,
                                   Callback callback, Exception lastError) {
        List<Map<String, Object>> deduped = dedupeById(merged);
        sortByTimestampDesc(deduped);

        if (!deduped.isEmpty()) {
            if (callback != null) callback.onLoaded(deduped);
            return;
        }
        if (app != null) {
            List<Map<String, Object>> local = LocalScanStore.loadAll(app, uid);
            if (!local.isEmpty()) {
                if (callback != null) callback.onLoaded(local);
                return;
            }
        }
        if (callback != null) {
            callback.onLoaded(new ArrayList<>());
        }
    }

    private static List<Map<String, Object>> dedupeById(List<Map<String, Object>> rows) {
        LinkedHashMap<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String key = id != null ? id.toString() : String.valueOf(row.hashCode());
            map.put(key, row);
        }
        return new ArrayList<>(map.values());
    }

    private static List<Map<String, Object>> toRows(com.google.firebase.firestore.QuerySnapshot snapshots) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (snapshots == null) return rows;
        for (QueryDocumentSnapshot doc : snapshots) {
            if (doc.getData() == null) continue;
            HashMap<String, Object> row = new HashMap<>(doc.getData());
            row.put("id", doc.getId());
            rows.add(row);
        }
        return rows;
    }

    private static void sortByTimestampDesc(List<Map<String, Object>> rows) {
        Collections.sort(rows, (a, b) -> Long.compare(timestampMillis(b), timestampMillis(a)));
    }

    private static long timestampMillis(Map<String, Object> row) {
        Object ts = row.get("timestamp");
        if (ts instanceof Timestamp) return ((Timestamp) ts).toDate().getTime();
        if (ts instanceof Long) return (Long) ts;
        return 0L;
    }

    public static class DashboardStats {
        public final int totalScans;
        public final int diseasesFound;
        public final String latestDiseaseKey;
        public final List<Map<String, Object>> recentScans;

        DashboardStats(int totalScans, int diseasesFound, String latestDiseaseKey,
                       List<Map<String, Object>> recentScans) {
            this.totalScans = totalScans;
            this.diseasesFound = diseasesFound;
            this.latestDiseaseKey = latestDiseaseKey;
            this.recentScans = recentScans != null ? recentScans : new ArrayList<>();
        }
    }

    public static DashboardStats computeStats(List<Map<String, Object>> scans) {
        if (scans == null || scans.isEmpty()) {
            return new DashboardStats(0, 0, "Healthy", new ArrayList<>());
        }

        int total = scans.size();
        int diseases = 0;
        String latestKey = "Healthy";

        for (int i = 0; i < scans.size(); i++) {
            Map<String, Object> doc = scans.get(i);
            String raw = doc.get("disease") != null ? doc.get("disease").toString() : null;
            if (raw == null && doc.get("diseaseName") != null) {
                raw = doc.get("diseaseName").toString();
            }
            String key = DiseaseLabels.normalizeKey(raw);
            if (i == 0) {
                latestKey = key;
            }
            if (DiseaseLabels.isDiseaseFound(key)) {
                diseases++;
            }
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (int i = 0; i < Math.min(3, scans.size()); i++) {
            recent.add(scans.get(i));
        }
        return new DashboardStats(total, diseases, latestKey, recent);
    }

    /** Mirror scan to users/{uid}/scans for reliable reads (no composite index). */
    static void mirrorToUserScans(String uid, String scanId, Map<String, Object> payload) {
        if (uid == null || scanId == null || scanId.isEmpty()) return;
        HashMap<String, Object> copy = new HashMap<>(payload);
        copy.put("id", scanId);
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("scans").document(scanId)
                .set(copy, SetOptions.merge());
    }
}
