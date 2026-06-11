package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * BaseActivity handles common functionality like logout, language wrapping, and data caching.
 */
public class BaseActivity extends AppCompatActivity {

    protected static final String PREFS_NAME = "UserPrefs";
    protected static final String KEY_ROLE = "user_role";
    protected static final String KEY_NAME = "user_name";
    protected static final String KEY_PHOTO = "user_photo";
    protected static final String KEY_FIRST_NAME = "user_first_name";
    protected static final String KEY_LAST_NAME = "user_last_name";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
     * Clears session and redirects to Login.
     */
    public void performLogout() {
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        Toast.makeText(this, R.string.logged_out_success, Toast.LENGTH_SHORT).show();
        finish();
    }
}
