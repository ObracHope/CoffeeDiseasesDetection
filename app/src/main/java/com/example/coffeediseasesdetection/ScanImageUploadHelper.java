package com.example.coffeediseasesdetection;

import android.content.Context;
import android.net.Uri;

import com.example.coffeediseasesdetection.data.LocalScanStore;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Upload scan images to Firebase Storage so web dashboard can display them. */
public final class ScanImageUploadHelper {

    private ScanImageUploadHelper() {
    }

    public static String storageFileName(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            return "scan_" + System.currentTimeMillis() + ".jpg";
        }
        String name = new File(imagePath).getName();
        if (name.matches("(?i)scan_\\d+\\.jpg")) return name;
        return "scan_" + System.currentTimeMillis() + ".jpg";
    }

    public static void uploadIfNeeded(String uid, String imagePath, String scanId) {
        if (uid == null || uid.isEmpty() || scanId == null || scanId.isEmpty()) return;
        if (imagePath == null || imagePath.isEmpty()) return;
        File file = new File(imagePath);
        if (!file.exists()) return;

        String name = storageFileName(imagePath);
        FirebaseStorage.getInstance().getReference("scan_images").child(uid).child(name)
                .putFile(Uri.fromFile(file))
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return FirebaseStorage.getInstance().getReference("scan_images")
                            .child(uid).child(name).getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    String url = uri.toString();
                    Map<String, Object> up = new HashMap<>();
                    up.put("imageUrl", url);
                    up.put("storagePath", "scan_images/" + uid + "/" + name);
                    FirebaseFirestore.getInstance().collection("scan_history")
                            .document(scanId).update(up);
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                            .collection("scans").document(scanId).update(up);
                    ScanRepository.mirrorScanPatchToRtdb(scanId, up);
                });
    }

    /** Upload local scans missing imageUrl — call on app start so web can load photos. */
    public static void syncPendingUploads(Context context, String uid) {
        if (context == null || uid == null || uid.isEmpty()) return;
        List<Map<String, Object>> rows = LocalScanStore.loadAll(context, uid);
        for (Map<String, Object> row : rows) {
            String url = row.get("imageUrl") != null ? row.get("imageUrl").toString().trim() : "";
            if (!url.isEmpty()) continue;
            String path = row.get("imagePath") != null ? row.get("imagePath").toString() : "";
            String id = row.get("id") != null ? row.get("id").toString() : "";
            uploadIfNeeded(uid, path, id);
        }
    }
}
