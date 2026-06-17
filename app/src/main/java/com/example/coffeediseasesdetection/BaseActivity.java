package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.exceptions.ClearCredentialException;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * BaseActivity handles logout, session checks, language wrapping, and data caching.
 */
public class BaseActivity extends AppCompatActivity {

    protected static final String PREFS_NAME = "UserPrefs";
    protected static final String KEY_ROLE = "user_role";
    protected static final String KEY_NAME = "user_name";
    protected static final String KEY_PHOTO = "user_photo";
    protected static final String KEY_FIRST_NAME = "user_first_name";
    protected static final String KEY_LAST_NAME = "user_last_name";

    private boolean logoutInProgress;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enforceSessionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!SessionManager.isAuthScreen(this) && !logoutInProgress) {
            if (!SessionManager.isSessionValid(this)) {
                performLogout();
                return;
            }
            SessionManager.touchActivity(this);
            updateUserPresence();
        }
    }

    private void enforceSessionIfNeeded() {
        if (SessionManager.isAuthScreen(this) || logoutInProgress) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            redirectToLogin();
            return;
        }
        if (!SessionManager.isSessionValid(this)) {
            performLogout();
        }
    }

    /** Track online/active status for admin dashboard real-time counts. */
    private void updateUserPresence() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> up = new HashMap<>();
        up.put("lastSeenAt", FieldValue.serverTimestamp());
        up.put("isOnline", true);
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .set(up, SetOptions.merge());
    }

    private void markUserOffline() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> up = new HashMap<>();
        up.put("lastSeenAt", FieldValue.serverTimestamp());
        up.put("isOnline", false);
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .set(up, SetOptions.merge());
        FcmTokenHelper.clearToken(user.getUid());
    }

    /**
     * Saves user details to SharedPreferences for instant UI rendering.
     */
    public void saveUserCache(String role, String name, String photoUrl, String firstName, String lastName) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        if (role != null) editor.putString(KEY_ROLE, role);
        if (name != null) editor.putString(KEY_NAME, name);
        if (photoUrl != null) editor.putString(KEY_PHOTO, photoUrl);
        if (firstName != null) editor.putString(KEY_FIRST_NAME, firstName);
        if (lastName != null) editor.putString(KEY_LAST_NAME, lastName);
        editor.apply();
    }

    /**
     * Clears Firebase session, secure storage, and redirects to login.
     * User must enter password again on next login.
     */
    public void performLogout() {
        if (logoutInProgress) return;
        logoutInProgress = true;

        ActivityLogHelper.logAuthLogout(this, "manual");
        markUserOffline();
        clearGoogleCredentialState();

        FirebaseAuth.getInstance().signOut();
        SessionManager.clearSession(this);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        Toast.makeText(this, R.string.logged_out_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void clearGoogleCredentialState() {
        try {
            CredentialManager credentialManager = CredentialManager.create(this);
            Executor executor = Executors.newSingleThreadExecutor();
            credentialManager.clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    null,
                    executor,
                    new androidx.credentials.CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override
                        public void onResult(Void result) { }

                        @Override
                        public void onError(ClearCredentialException e) { }
                    });
        } catch (Exception ignored) {
        }
    }
}
