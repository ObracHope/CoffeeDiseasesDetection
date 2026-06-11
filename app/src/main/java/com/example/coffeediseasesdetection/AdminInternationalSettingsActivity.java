package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.coffeediseasesdetection.admin.AdminRepository;

/** Admin settings: language, theme, password — international-style panel. */
public class AdminInternationalSettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.admin_intl_settings);

        ThemeHelper.applySavedTheme(this);
        new AdminRepository().logActivity(this, "settings", "Opened international admin settings");

        com.google.android.material.materialswitch.MaterialSwitch switchLanguage =
                findViewById(R.id.switchLanguage);
        android.widget.TextView tvLanguageStatus = findViewById(R.id.tvLanguageStatus);
        String currentLang = LocaleHelper.getLanguage(this);
        if (switchLanguage != null) {
            switchLanguage.setChecked("sw".equals(currentLang));
            if (tvLanguageStatus != null) {
                tvLanguageStatus.setText("sw".equals(currentLang)
                        ? getString(R.string.kiswahili) : getString(R.string.english));
            }
            switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String lang = isChecked ? LocaleHelper.LANG_SW : LocaleHelper.LANG_EN;
                if (!lang.equals(LocaleHelper.getLanguage(this))) {
                    LocaleHelper.setLanguageAndRestart(this, lang);
                }
            });
        }

        com.google.android.material.materialswitch.MaterialSwitch switchTheme =
                findViewById(R.id.switchTheme);
        android.widget.TextView tvThemeStatus = findViewById(R.id.tvThemeStatus);
        int mode = ThemeHelper.getSavedMode(this);
        if (switchTheme != null) {
            switchTheme.setChecked(mode == AppCompatDelegate.MODE_NIGHT_YES);
            if (tvThemeStatus != null) {
                tvThemeStatus.setText(mode == AppCompatDelegate.MODE_NIGHT_YES
                        ? getString(R.string.theme_dark_mode) : getString(R.string.theme_light_mode));
            }
            switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ThemeHelper.saveAndApply(this, isChecked);
                recreate();
            });
        }

        android.widget.Button btnChangePassword = findViewById(R.id.btnChangePassword);
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ChangePasswordActivity.class)));
        }

        androidx.cardview.widget.CardView cardManageUsers = findViewById(R.id.cardManageUsers);
        if (cardManageUsers != null) cardManageUsers.setVisibility(android.view.View.VISIBLE);
        android.widget.Button btnManageUsers = findViewById(R.id.btnManageUsers);
        if (btnManageUsers != null) {
            btnManageUsers.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminManageFarmersActivity.class)));
        }

        android.widget.Button btnNotifications = findViewById(R.id.btnNotifications);
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationSettingsActivity.class)));
        }
    }
}
