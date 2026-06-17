package com.example.coffeediseasesdetection;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Admin-only password reset: role checks, default password, Cloud Function update. */
public final class AdminPasswordResetHelper {

    private static final String REGION = "us-central1";
    private static final String FN_ADMIN_RESET = "adminResetPassword";

    public interface ResetCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private AdminPasswordResetHelper() {}

    /** Roles allowed to use admin reset at all. */
    public static boolean canUseAdminReset(String actorRole) {
        String r = AuthHelper.normalizeRole(actorRole);
        return "system_admin".equals(r) || "superadmin".equals(r) || "main".equals(r)
                || "admin".equals(r) || "it".equals(r);
    }

    /**
     * System Admin / IT / Superadmin → anyone.
     * Admin → farmers only.
     */
    public static boolean canResetTarget(String actorRole, String targetRole) {
        if (!canUseAdminReset(actorRole)) return false;
        String actor = AuthHelper.normalizeRole(actorRole);
        String target = AuthHelper.normalizeRole(targetRole);
        if ("system_admin".equals(actor) || "superadmin".equals(actor)
                || "main".equals(actor) || "it".equals(actor)) {
            return true;
        }
        if ("admin".equals(actor)) {
            return "farmer".equals(target);
        }
        return false;
    }

    public static String extractFirstName(Map<String, Object> user) {
        if (user == null) return "User";
        Object fn = user.get("firstName");
        if (fn != null && !fn.toString().trim().isEmpty()) return fn.toString().trim();
        Object name = user.get("name");
        if (name != null) {
            String[] parts = name.toString().trim().split("\\s+", 2);
            if (parts.length > 0 && !parts[0].isEmpty()) return parts[0];
        }
        Object email = user.get("email");
        if (email != null && email.toString().contains("@")) {
            String local = email.toString().substring(0, email.toString().indexOf('@'));
            if (!local.isEmpty()) return local;
        }
        return "User";
    }

    /** Default: FirstName + 123 (e.g. Obeid123, Halima123). */
    public static String generateDefaultPassword(String firstName) {
        if (TextUtils.isEmpty(firstName)) return "User123";
        String trimmed = firstName.trim();
        String capped = trimmed.substring(0, 1).toUpperCase(Locale.US)
                + (trimmed.length() > 1 ? trimmed.substring(1) : "");
        return capped + "123";
    }

    /** Last password set by admin (stored in Firestore). Firebase Auth hash is not readable. */
    public static String getStoredOldPassword(Map<String, Object> user) {
        if (user == null) return null;
        Object v = user.get("lastSetPassword");
        if (v != null && !v.toString().trim().isEmpty()) return v.toString().trim();
        return null;
    }

    public static void resetPassword(@NonNull Context context,
                                     @NonNull String targetUid,
                                     @NonNull String newPassword,
                                     @NonNull ResetCallback callback) {
        if (newPassword.length() < 6) {
            callback.onError(context.getString(R.string.password_min));
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onError("Not signed in");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("targetUid", targetUid);
        payload.put("newPassword", newPassword);

        FirebaseFunctions.getInstance(REGION)
                .getHttpsCallable(FN_ADMIN_RESET)
                .call(payload)
                .addOnCompleteListener((Task<HttpsCallableResult> task) -> {
                    if (task.isSuccessful() && task.getResult() != null
                            && task.getResult().getData() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) task.getResult().getData();
                        Object ok = data.get("success");
                        if (Boolean.TRUE.equals(ok)) {
                            callback.onSuccess(context.getString(R.string.reset_password_success));
                        } else {
                            callback.onError(context.getString(R.string.reset_password_failed));
                        }
                    } else {
                        Exception e = task.getException();
                        String msg = e != null && e.getMessage() != null
                                ? e.getMessage() : context.getString(R.string.reset_password_failed);
                        callback.onError(msg);
                    }
                });
    }
}
