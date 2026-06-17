package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Unified activity + authentication logging for web and mobile (Firestore admin_activity_logs).
 */
public final class ActivityLogHelper {

    private ActivityLogHelper() {
    }

    public static void log(@NonNull Context context, @NonNull String action,
                           @Nullable Map<String, Object> details) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Map<String, Object> log = new HashMap<>();
        log.put("action", action);
        log.put("platform", "mobile");
        log.put("createdAtMs", System.currentTimeMillis());
        log.put("timestamp", FieldValue.serverTimestamp());

        Map<String, Object> detailMap = details != null ? new HashMap<>(details) : new HashMap<>();
        log.put("details", detailMap);

        String summary = summarizeDetails(detailMap);
        if (!summary.isEmpty()) {
            log.put("detail", summary);
        }

        if (user != null) {
            log.put("adminUid", user.getUid());
            log.put("uid", user.getUid());
            if (user.getEmail() != null) {
                log.put("adminEmail", user.getEmail());
            }
            String name = resolveDisplayName(context, user);
            log.put("adminName", name);
            if (!detailMap.containsKey("role")) {
                detailMap.put("role", readRole(context));
            }
        }

        FirebaseFirestore.getInstance().collection("admin_activity_logs").add(log);
    }

    public static void log(@NonNull Context context, @NonNull String action, @NonNull String detail) {
        Map<String, Object> details = new HashMap<>();
        details.put("summary", detail);
        log(context, action, details);
    }

    public static void logAuthLogin(@NonNull Context context, @NonNull String role,
                                    @NonNull String method) {
        Map<String, Object> details = new HashMap<>();
        details.put("role", role);
        details.put("method", method);
        details.put("platform", "mobile");
        String action = isStaffRole(role) ? "admin_login" : "farmer_login";
        log(context, action, details);
    }

    public static void logAuthLogout(@NonNull Context context, @NonNull String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        details.put("platform", "mobile");
        details.put("role", readRole(context));
        log(context, "auth_logout", details);
    }

    private static String readRole(Context context) {
        return context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(BaseActivity.KEY_ROLE, "farmer");
    }

    private static String resolveDisplayName(Context context, FirebaseUser user) {
        SharedPreferences prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String first = prefs.getString(BaseActivity.KEY_FIRST_NAME, "");
        String last = prefs.getString(BaseActivity.KEY_LAST_NAME, "");
        String full = ProfileHelper.fullName(first, last, prefs.getString(BaseActivity.KEY_NAME, ""));
        if (!full.isEmpty()) return full;
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        }
        if (user.getEmail() != null) return user.getEmail();
        return "User";
    }

    private static boolean isStaffRole(String role) {
        String r = role != null ? role.toLowerCase(Locale.US) : "";
        return r.equals("admin") || r.equals("system_admin") || r.equals("superadmin")
                || r.equals("main") || r.equals("it") || r.equals("technician")
                || r.equals("bwana_kilimo") || r.equals("waziri_wa_kilimo");
    }

    private static String summarizeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return "";
        Object summary = details.get("summary");
        if (summary != null && !String.valueOf(summary).trim().isEmpty()) {
            return String.valueOf(summary);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : details.entrySet()) {
            if (e.getValue() == null) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }
}
