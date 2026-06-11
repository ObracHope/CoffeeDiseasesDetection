package com.example.coffeediseasesdetection;

import android.content.Context;
import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.coffeediseasesdetection.data.LocalScanStore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public final class ScanRepository {

    public interface SaveCallback {
        void onSuccess(String scanId);
        void onError(Exception e);
    }

    private ScanRepository() {}

    public static void saveScan(Context context, String diseaseKey, float confidence, String description,
                                String imagePath, boolean isCoffee, boolean isHealthy,
                                SaveCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onError(new IllegalStateException("Not logged in"));
            return;
        }

        String persistedPath = persistImageLocally(context, imagePath);
        Map<String, Object> payload = buildPayload(context, user, diseaseKey, confidence, description,
                persistedPath, isCoffee, isHealthy);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();

        commit(context, db, uid, payload, diseaseKey, callback);

        if (persistedPath != null && new File(persistedPath).exists()) {
            uploadImageUrlInBackground(uid, persistedPath, null);
        }

        enrichLocationInBackground(context, db, uid);
    }

    private static Map<String, Object> buildPayload(Context context, FirebaseUser user, String diseaseKey,
                                                    float confidence, String description, String imagePath,
                                                    boolean isCoffee, boolean isHealthy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getUid());
        payload.put("userEmail", user.getEmail() != null ? user.getEmail() : "");
        payload.put("disease", diseaseKey);
        payload.put("diseaseName", DiseaseTextProvider.displayName(context, diseaseKey));
        payload.put("isCoffee", isCoffee);
        payload.put("isHealthy", isHealthy);
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

        if (DiseaseLabels.isDiseaseFound(diseaseKey)) {
            AiRecommendationProvider.Recommendation rec =
                    AiRecommendationProvider.forDisease(context, diseaseKey);
            payload.put("recommendedMedicine", rec.medicines);
            payload.put("preventionTips", rec.prevention);
            payload.put("nextScanDays", rec.nextScanDays);
        }
        return payload;
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
            db.collection("users").document(uid).update(up);
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
                    }
                });
    }

    private static void uploadImageUrlInBackground(String uid, String path, String scanId) {
        String name = "scan_" + System.currentTimeMillis() + ".jpg";
        FirebaseStorage.getInstance().getReference("scan_images").child(uid).child(name)
                .putFile(Uri.fromFile(new File(path)))
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) throw t.getException();
                    return FirebaseStorage.getInstance().getReference("scan_images")
                            .child(uid).child(name).getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    if (scanId == null || scanId.isEmpty()) return;
                    String url = uri.toString();
                    Map<String, Object> up = new HashMap<>();
                    up.put("imageUrl", url);
                    FirebaseFirestore.getInstance().collection("scan_history")
                            .document(scanId).update(up);
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                            .collection("scans").document(scanId)
                            .update("imageUrl", url);
                });
    }

    private static void commit(Context ctx, FirebaseFirestore db, String uid, Map<String, Object> payload,
                               String diseaseKey, SaveCallback callback) {
        db.collection("scan_history").add(payload).addOnSuccessListener(ref -> {
            String scanId = ref.getId();
            payload.put("id", scanId);

            HashMap<String, Object> localCopy = new HashMap<>(payload);
            localCopy.put("timestamp", System.currentTimeMillis());
            LocalScanStore.save(ctx, scanId, localCopy);
            ScanHistoryLoader.mirrorToUserScans(uid, scanId, payload);

            String imagePath = payload.get("imagePath") != null ? payload.get("imagePath").toString() : "";
            if (!imagePath.isEmpty() && new File(imagePath).exists()) {
                uploadImageUrlInBackground(uid, imagePath, scanId);
            }
            enrichScanRegionInBackground(db, uid, scanId);

            Map<String, Object> userUp = new HashMap<>();
            userUp.put("lastScanAt", FieldValue.serverTimestamp());
            db.collection("users").document(uid).set(userUp, SetOptions.merge());

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
