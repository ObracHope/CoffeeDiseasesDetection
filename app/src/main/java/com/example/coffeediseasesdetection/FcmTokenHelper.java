package com.example.coffeediseasesdetection;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Saves FCM token to RTDB + Firestore so Cloud Functions can send geo-alerts.
 */
public final class FcmTokenHelper {

    private static final String TAG = "FcmTokenHelper";

    private FcmTokenHelper() {}

    public static void refreshAndSave() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> saveToken(user.getUid(), token))
                .addOnFailureListener(e -> Log.w(TAG, "FCM token fetch failed", e));
    }

    public static void saveToken(String uid, String token) {
        if (uid == null || token == null || token.isEmpty()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("fcmToken", token);
        payload.put("tokenUpdatedAt", System.currentTimeMillis());

        FirebaseDatabase.getInstance(AuthHelper.RTDB_URL)
                .getReference("users")
                .child(uid)
                .updateChildren(payload);

        Map<String, Object> fs = new HashMap<>();
        fs.put("fcmToken", token);
        fs.put("tokenUpdatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(fs, com.google.firebase.firestore.SetOptions.merge());
    }
}
