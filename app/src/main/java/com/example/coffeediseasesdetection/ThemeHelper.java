package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeHelper {

    public static final String PREFS_NAME = "AppPrefs";
    public static final String KEY_THEME = "theme_mode";

    private ThemeHelper() {}

    public static void applySavedTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(getSavedMode(context));
    }

    public static int getSavedMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void saveAndApply(Context context, boolean darkMode) {
        int mode = darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME, mode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
