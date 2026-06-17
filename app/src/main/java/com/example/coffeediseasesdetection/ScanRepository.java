package com.example.coffeediseasesdetection;

import android.content.Context;
import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.coffeediseasesdetection.data.LocalScanStore;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ScanRepository {

    public static final String SOURCE_CAMERA = "camera";
    public static final String SOURCE_UPLOAD = "upload";

    public interface SaveCallback {
        void onSuccess(String scanId);
        void onError(Exception e);
    }

    private ScanRepository() {}

    public static void saveScan(Context context, String diseaseKey, float confidence, String description,
                                String imagePath, boolean isCoffee, boolean isHealthy,
                                SaveCallback callback) {
        saveScan(context, diseaseKey, confidence, description, imagePath, isCoffee, isHealthy,
                SOURCE_CAMERA, callback);
    }

    public static void saveScan(Context context, String diseaseKey, float confidence, String description,
                                String imagePath, boolean isCoffee, boolean isHealthy,
                                String scanSource, SaveCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onError(new IllegalStateException("Not logged in"));
            return;
        }

        String persistedPath = persistImageLocally(context, imagePath);
        Map<String, Object> payload = buildPayload(context, user, diseaseKey, confidence, description,
                persistedPath, isCoffee, isHealthy, scanSource);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();

        commit(context, db, uid, payload, diseaseKey, scanSource, callback);
        enrichLocationInBackground(context, db, uid);
    }

    private static Map<String, Object> buildPayload(Context context, FirebaseUser user, String diseaseKey,
                                                    float confidence, String description, String imagePath,
                                                    boolean isCoffee, boolean isHealthy, String scanSource) {
        boolean notCoffee = !isCoffee || DiseaseDetector.NOT_COFFEE_LABEL.equals(diseaseKey)
                || "IsNotCoffee".equals(diseaseKey);
        boolean healthCoffee = isCoffee && isHealthy && !notCoffee;

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getUid());
        payload.put("userEmail", user.getEmail() != null ? user.getEmail() : "");
        payload.put("disease", diseaseKey);
        payload.put("diseaseName", DiseaseTextProvider.displayName(context, diseaseKey));
        payload.put("isCoffee", isCoffee);
        payload.put("isHealthy", isHealthy);
        payload.put("healthCoffee", healthCoffee);
        payload.put("notCoffee", notCoffee);
        payload.put("resultCategory", resultCategory(diseaseKey, isCoffee, isHealthy, notCoffee));
        payload.put("scanSource", scanSource != null ? scanSource : SOURCE_CAMERA);
        payload.put("scanType", scanSource != null ? scanSource : SOURCE_CAMERA);
        payload.put("confidence", confidence);
        payload.put("description", description != null ? description : "");
        payload.put("imagePath", imagePath != null ? imagePath : "");
        payload.put("imageUrl", "");
        payload.put("region", context.getString(R.string.unknown));
        payload.put("district", context.getString(R.string.unknown));
        payload.put("ward", "");
        payload.put("latitude", 0.0);
        payload.put("longitude", 0.0);
        payload.put("medicineUsed", "");
        payload.put("treatmentProgress", "pending");
        payload.put("previousScanId", "");
        payload.put("timestamp", FieldValue.serverTimestamp());
        payload.put("createdAtMs", System.currentTimeMillis());

        if (DiseaseLabels.isDiseaseFound(diseaseKey)) {
            AiRecommendationProvider.Recommendation rec =
                    AiRecommendationProvider.forDisease(context, diseaseKey);
            payload.put("recommendedMedicine", rec.medicines);
            payload.put("preventionTips", rec.prevention);
            payload.put("nextScanDays", rec.nextScanDays);
        }
        return payload;
    }

    private static String resultCategory(String diseaseKey, boolean isCoffee, boolean isHealthy,
                                         boolean notCoffee) {
        if (notCoffee) return "not_coffee";
        if (isHealthy || "Healthy".equals(diseaseKey)) return "health_coffee";
        if ("Uncertain".equals(diseaseKey)) return "uncertain";
        if (DiseaseLabels.isDiseaseFound(diseaseKey)) return "disease";
        return "other";
    }

    /** Copy scan image into app storage so history thumbnails survive cache clears. */
    static String persistImageLocally(Context context, String sourcePath) {
        if (sourcePath == null || sourcePath.isEmpty()) return "";
        File source = new File(sourcePath);
        if (!source.exists()) return sourcePath;

        File dir = new File(context.getFilesDir(), "scan_images");
        if (!dir.exists() && !dir.mkdirs()) return sourcePath;

        File dest = new File(dir, "scan_" + System.currentTimeMillis() + ".jpg");
        try (FileChannel in = new FileInputStream(source).getChannel();
             FileChannel out = new FileOutputStream(dest).getChannel()) {
            out.transferFrom(in, 0, in.size());
            return dest.getAbsolutePath();
        } catch (IOException e) {
            return sourcePath;
        }
    }

    private static void enrichLocationInBackground(Context context, FirebaseFirestore db, String uid) {
        LocationHelper.fetchLocation(context, loc -> {
            if (loc == null) return;
            Map<String, Object> up = new HashMap<>();
            up.put("lastLatitude", loc.latitude);
            up.put("lastLongitude", loc.longitude);
            up.put("lastSeenAt", FieldValue.serverTimestamp());
            db.collection("users").document(uid).set(up, SetOptions.merge());
        });
    }

    private static void enrichScanLocationInBackground(Context context, FirebaseFirestore db,
                                                         String uid, String scanId) {
        LocationHelper.fetchLocation(context, loc -> {
            if (loc == null || scanId == null || scanId.isEmpty()) return;
            Map<String, Object> patch = new HashMap<>();
            patch.put("latitude", loc.latitude);
            patch.put("longitude", loc.longitude);
            db.collection("scan_history").document(scanId).update(patch);
            db.collection("users").document(uid).collection("scans").document(scanId).update(patch);
            mirrorScanPatchToRtdb(scanId, patch);
        });
    }

    private static void enrichScanRegionInBackground(FirebaseFirestore db, String uid, String scanId) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists() || scanId == null || scanId.isEmpty()) return;
                    Map<String, Object> regionPatch = new HashMap<>();
                    if (userDoc.getString("region") != null) regionPatch.put("region", userDoc.getString("region"));
                    if (userDoc.getString("district") != null) regionPatch.put("district", userDoc.getString("district"));
                    if (userDoc.getString("ward") != null) regionPatch.put("ward", userDoc.getString("ward"));
                    if (!regionPatch.isEmpty()) {
                        db.collection("scan_history").document(scanId).update(regionPatch);
                        db.collection("users").document(uid).collection("scans").document(scanId).update(regionPatch);
                        mirrorScanPatchToRtdb(scanId, regionPatch);
                    }
                });
    }

    private static void uploadImageUrlInBackground(String uid, String path, String scanId) {
        ScanImageUploadHelper.uploadIfNeeded(uid, path, scanId);
    }

    private static void commit(Context ctx, FirebaseFirestore db, String uid, Map<String, Object> payload,
                               String diseaseKey, String scanSource, SaveCallback callback) {
        db.collection("scan_history").add(payload).addOnSuccessListener(ref -> {
            String scanId = ref.getId();
            payload.put("id", scanId);

            HashMap<String, Object> localCopy = new HashMap<>(payload);
            localCopy.put("timestamp", System.currentTimeMillis());
            LocalScanStore.save(ctx, scanId, localCopy);
            ScanHistoryLoader.mirrorToUserScans(uid, scanId, payload);
            mirrorScanToRtdb(scanId, payload);

            String imagePath = payload.get("imagePath") != null ? payload.get("imagePath").toString() : "";
            if (!imagePath.isEmpty() && new File(imagePath).exists()) {
                uploadImageUrlInBackground(uid, imagePath, scanId);
            }
            enrichScanRegionInBackground(db, uid, scanId);
            enrichScanLocationInBackground(ctx, db, uid, scanId);

            Map<String, Object> userUp = new HashMap<>();
            userUp.put("lastScanAt", FieldValue.serverTimestamp());
            userUp.put("lastSeenAt", FieldValue.serverTimestamp());
            db.collection("users").document(uid).set(userUp, SetOptions.merge());

            writeScanActivityLog(db, scanId, uid, payload, scanSource);
            updateDailyReport(db, payload, scanSource);

            double lat = payload.get("latitude") instanceof Number
                    ? ((Number) payload.get("latitude")).doubleValue() : 0;
            double lng = payload.get("longitude") instanceof Number
                    ? ((Number) payload.get("longitude")).doubleValue() : 0;

            if (DiseaseLabels.isDiseaseFound(diseaseKey)) {
                GeoAlertManager.notifyNearbyFarmers(ctx, db, uid, scanId, diseaseKey, lat, lng);
                String imageUrl = payload.get("imageUrl") != null ? payload.get("imageUrl").toString() : "";
                String diseaseName = payload.get("diseaseName") != null
                        ? payload.get("diseaseName").toString()
                        : DiseaseTextProvider.displayName(ctx, diseaseKey);
                Object conf = payload.get("confidence");
                String confStr = conf instanceof Number
                        ? String.format(java.util.Locale.getDefault(), "%.1f%%", ((Number) conf).floatValue())
                        : (conf != null ? conf.toString() : "--");
                NotificationHelper.createDiseaseDetectionNotification(ctx, scanId,
                        diseaseName, confStr, imageUrl, imagePath);
            }
            if (callback != null) callback.onSuccess(scanId);
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onError(e);
        });
    }

    private static void writeScanActivityLog(FirebaseFirestore db, String scanId, String uid,
                                             Map<String, Object> payload, String scanSource) {
        Map<String, Object> log = new HashMap<>();
        log.put("action", "scan_completed");
        log.put("scanId", scanId);
        log.put("userId", uid);
        log.put("userEmail", payload.get("userEmail"));
        log.put("disease", payload.get("disease"));
        log.put("diseaseName", payload.get("diseaseName"));
        log.put("scanSource", scanSource != null ? scanSource : SOURCE_CAMERA);
        log.put("resultCategory", payload.get("resultCategory"));
        log.put("isCoffee", payload.get("isCoffee"));
        log.put("isHealthy", payload.get("isHealthy"));
        log.put("healthCoffee", payload.get("healthCoffee"));
        log.put("notCoffee", payload.get("notCoffee"));
        log.put("confidence", payload.get("confidence"));
        log.put("region", payload.get("region"));
        log.put("district", payload.get("district"));
        log.put("timestamp", FieldValue.serverTimestamp());
        log.put("createdAtMs", System.currentTimeMillis());
        log.put("platform", "mobile");
        db.collection("scan_activity_logs").add(log);

        Map<String, Object> adminLog = new HashMap<>(log);
        adminLog.put("action", SOURCE_UPLOAD.equals(scanSource) ? "image_upload" : "scan_camera");
        adminLog.put("platform", "mobile");
        adminLog.put("adminName", payload.get("userEmail"));
        adminLog.put("adminEmail", payload.get("userEmail"));
        Map<String, Object> details = new HashMap<>();
        details.put("disease", payload.get("diseaseName"));
        details.put("confidence", payload.get("confidence"));
        details.put("scanSource", scanSource);
        details.put("resultCategory", payload.get("resultCategory"));
        adminLog.put("details", details);
        db.collection("admin_activity_logs").add(adminLog);
    }

    private static void updateDailyReport(FirebaseFirestore db, Map<String, Object> payload, String scanSource) {
        String dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Map<String, Object> inc = new HashMap<>();
        inc.put("totalScans", FieldValue.increment(1));
        inc.put("date", dayKey);
        inc.put("updatedAt", FieldValue.serverTimestamp());

        if (SOURCE_UPLOAD.equals(scanSource)) {
            inc.put("totalUploads", FieldValue.increment(1));
        } else {
            inc.put("totalCameraScans", FieldValue.increment(1));
        }

        Object cat = payload.get("resultCategory");
        if ("disease".equals(cat)) {
            inc.put("diseasesDetected", FieldValue.increment(1));
        } else if ("health_coffee".equals(cat)) {
            inc.put("healthCoffee", FieldValue.increment(1));
        } else if ("not_coffee".equals(cat)) {
            inc.put("notCoffee", FieldValue.increment(1));
        }

        db.collection("daily_reports").document(dayKey).set(inc, SetOptions.merge());
    }

    private static void mirrorScanToRtdb(String scanId, Map<String, Object> payload) {
        try {
            HashMap<String, Object> copy = new HashMap<>(payload);
            copy.remove("timestamp");
            copy.put("createdAtMs", System.currentTimeMillis());
            FirebaseDatabase.getInstance(AuthHelper.RTDB_URL)
                    .getReference("scan_history").child(scanId).setValue(copy);
        } catch (Exception ignored) {
        }
    }

    public static void mirrorScanPatchToRtdb(String scanId, Map<String, Object> patch) {
        try {
            FirebaseDatabase.getInstance(AuthHelper.RTDB_URL)
                    .getReference("scan_history").child(scanId).updateChildren(patch);
        } catch (Exception ignored) {
        }
    }

    public static void updateTreatment(Context ctx, String scanId, String medicine, String progress) {
        if (scanId == null || scanId.isEmpty()) return;
        Map<String, Object> up = new HashMap<>();
        up.put("medicineUsed", medicine);
        up.put("treatmentProgress", progress);
        up.put("treatmentUpdatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("scan_history").document(scanId).update(up)
                .addOnSuccessListener(v -> {
                    NotificationScheduler.scheduleTreatmentReminders(ctx, scanId);
                    NotificationHelper.showNotification(ctx,
                            ctx.getString(R.string.treatment_saved_title),
                            ctx.getString(R.string.treatment_saved_body));
                });
    }
}
