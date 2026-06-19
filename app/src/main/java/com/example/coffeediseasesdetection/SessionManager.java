package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Session metadata: Firebase ID token lifecycle, 24h max session, 30 min inactivity logout.
 */
public final class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREFS = "secure_session";
    private static final String KEY_UID = "session_uid";
    private static final String KEY_STARTED = "session_started_at";
    private static final String KEY_LAST_ACTIVITY = "last_activity_at";

    /** Maximum session age — 24 hours. */
    public static final long SESSION_MAX_AGE_MS = 24L * 60L * 60L * 1000L;
    /** Auto logout after inactivity — 10 minutes. */
    public static final long INACTIVITY_TIMEOUT_MS = 10L * 60L * 1000L;

    private SessionManager() {}

    public static void onLoginSuccess(@NonNull Context context, @NonNull FirebaseUser user) {
        writeSession(context, user.getUid(), System.currentTimeMillis());
        user.getIdToken(true).addOnFailureListener(e ->
                Log.w(TAG, "ID token refresh on login failed", e));
    }

    /** Persist session immediately so dashboard does not log out on first onCreate. */
    private static void writeSession(@NonNull Context context, @NonNull String uid, long now) {
        getPrefs(context).edit()
                .putString(KEY_UID, uid)
                .putLong(KEY_STARTED, now)
                .putLong(KEY_LAST_ACTIVITY, now)
                .commit();
    }

    public static void touchActivity(@NonNull Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        SharedPreferences prefs = getPrefs(context);
        if (!user.getUid().equals(prefs.getString(KEY_UID, ""))) return;
        prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply();
    }

    public static boolean isSessionValid(@NonNull Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return false;

        SharedPreferences prefs = getPrefs(context);
        String storedUid = prefs.getString(KEY_UID, "");
        long now = System.currentTimeMillis();

        // Fresh login race: Firebase auth succeeded before session metadata finished writing.
        if (storedUid.isEmpty()) {
            writeSession(context, user.getUid(), now);
            return true;
        }
        if (!storedUid.equals(user.getUid())) {
            return false;
        }

        long started = prefs.getLong(KEY_STARTED, 0L);
        long lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, 0L);

        if (started > 0L && now - started > SESSION_MAX_AGE_MS) {
            return false;
        }
        if (lastActivity > 0L && now - lastActivity > INACTIVITY_TIMEOUT_MS) {
            return false;
        }
        return true;
    }

    public static void clearSession(@NonNull Context context) {
        getPrefs(context).edit().clear().apply();
        context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("admin_data_hub_cache", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static boolean isAuthScreen(@NonNull Context context) {
        return context instanceof MainActivity
                || context instanceof LandingActivity
                || context instanceof RegisterActivity
                || context instanceof ForgotPasswordActivity;
    }

    private static SharedPreferences getPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using private prefs", e);
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }
}
