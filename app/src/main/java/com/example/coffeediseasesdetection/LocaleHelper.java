package com.example.coffeediseasesdetection;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * Global app locale — persisted in app_prefs (survives logout and app restart).
 */
public final class LocaleHelper {

    public static final String PREFS_NAME = "app_prefs";
    public static final String KEY_LANG = "app_language";
    public static final String LANG_EN = "en";
    public static final String LANG_SW = "sw";

    private LocaleHelper() {}

    public static Context wrap(Context context) {
        return updateResources(context, getLanguage(context));
    }

    public static void applyAppLocale(Context context) {
        String lang = getLanguage(context);
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        }
    }

    public static void setLanguage(Context context, String languageCode) {
        persist(context.getApplicationContext(), languageCode);
        applyAppLocale(context.getApplicationContext());
    }

    /** Change language and restart app UI so every screen updates instantly. */
    public static void setLanguageAndRestart(Activity activity, String languageCode) {
        persist(activity.getApplicationContext(), languageCode);
        applyAppLocale(activity.getApplicationContext());

        Intent intent = new Intent(activity, FarmerDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finishAffinity();
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, LANG_EN);
    }

    public static boolean isSwahili(Context context) {
        return LANG_SW.equals(getLanguage(context));
    }

    private static void persist(Context context, String languageCode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANG, languageCode)
                .apply();
    }

    private static Context updateResources(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        }
        config.locale = locale;
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        return context;
    }
}
