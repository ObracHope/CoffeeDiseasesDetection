package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends BaseActivity {

    private SharedPreferences prefs;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings);

        prefs = getSharedPreferences(ThemeHelper.PREFS_NAME, MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        ThemeHelper.applySavedTheme(this);

        // Language Switch logic
        com.google.android.material.materialswitch.MaterialSwitch switchLanguage = findViewById(R.id.switchLanguage);
        android.widget.TextView tvLanguageStatus = findViewById(R.id.tvLanguageStatus);
        String currentLang = LocaleHelper.getLanguage(this);
        if (switchLanguage != null) {
            switchLanguage.setOnCheckedChangeListener(null);
            switchLanguage.setChecked("sw".equals(currentLang));
            if (tvLanguageStatus != null) {
                tvLanguageStatus.setText("sw".equals(currentLang)
                        ? getString(R.string.kiswahili) : getString(R.string.english));
            }
            switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String lang = isChecked ? LocaleHelper.LANG_SW : LocaleHelper.LANG_EN;
                if (!lang.equals(LocaleHelper.getLanguage(SettingsActivity.this))) {
                    LocaleHelper.setLanguageAndRestart(SettingsActivity.this, lang);
                }
            });
        }
        
        // Theme Switch logic
        com.google.android.material.materialswitch.MaterialSwitch switchTheme = findViewById(R.id.switchTheme);
        android.widget.TextView tvThemeStatus = findViewById(R.id.tvThemeStatus);
        int currentNightMode = ThemeHelper.getSavedMode(this);
        if (switchTheme != null) {
            switchTheme.setChecked(currentNightMode == AppCompatDelegate.MODE_NIGHT_YES);
            if (tvThemeStatus != null) {
                tvThemeStatus.setText(currentNightMode == AppCompatDelegate.MODE_NIGHT_YES
                        ? getString(R.string.theme_dark_mode) : getString(R.string.theme_light_mode));
            }
            switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ThemeHelper.saveAndApply(SettingsActivity.this, isChecked);
                if (tvThemeStatus != null) {
                    tvThemeStatus.setText(isChecked
                            ? getString(R.string.theme_dark_mode) : getString(R.string.theme_light_mode));
                }
                recreate();
            });
        }

        // Change password
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        }

        // Admin section visibility
        CardView cardManageUsers = findViewById(R.id.cardManageUsers);
        Button btnManageUsers = findViewById(R.id.btnManageUsers);
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && cardManageUsers != null) {
            firestore.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists() && "admin".equalsIgnoreCase(snapshot.getString("role"))) {
                            cardManageUsers.setVisibility(android.view.View.VISIBLE);
                        }
                    });
        }
        if (btnManageUsers != null) {
            btnManageUsers.setOnClickListener(v -> startActivity(new Intent(this, AdminManageFarmersActivity.class)));
        }

        // Notifications
        if (findViewById(R.id.btnNotifications) != null) {
            findViewById(R.id.btnNotifications).setOnClickListener(v -> 
                    startActivity(new Intent(this, NotificationSettingsActivity.class)));
        }

        // Logout Button - Inatumia performLogout() ya BaseActivity sasa
        Button btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> performLogout());
        }

        bindLegalNavigation(R.id.btnHelpSupport, HelpSupportActivity.class);
        bindLegalNavigation(R.id.btnAboutUs, AboutUsActivity.class);
        bindLegalNavigation(R.id.btnTerms, TermsConditionsActivity.class);
        bindLegalNavigation(R.id.btnPrivacy, PrivacyPolicyActivity.class);
    }

    private void bindLegalNavigation(int viewId, Class<?> activityClass) {
        android.view.View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(v -> startActivity(new Intent(this, activityClass)));
        }
    }

}
