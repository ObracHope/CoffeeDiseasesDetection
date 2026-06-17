package com.example.coffeediseasesdetection;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

/** Maps latest scan status to aligned risk level for farmer dashboard. */
public final class DiseaseRiskHelper {

    private DiseaseRiskHelper() {}

    @StringRes
    public static int riskLabelRes(String diseaseKey) {
        String k = DiseaseLabels.normalizeKey(diseaseKey);
        if ("Healthy".equals(k)) return R.string.risk_low;
        if ("IsNotCoffee".equals(k) || "Uncertain".equals(k) || "Unknown".equals(k)) {
            return R.string.risk_none;
        }
        if ("Rust".equals(k) || "BerryDisease".equals(k) || "Wilt".equals(k)) {
            return R.string.risk_high;
        }
        return R.string.risk_medium;
    }

    @ColorRes
    public static int riskColorRes(String diseaseKey) {
        String k = DiseaseLabels.normalizeKey(diseaseKey);
        if ("Healthy".equals(k)) return R.color.status_healthy;
        if ("IsNotCoffee".equals(k) || "Uncertain".equals(k) || "Unknown".equals(k)) {
            return R.color.textLight;
        }
        if ("Rust".equals(k) || "BerryDisease".equals(k) || "Wilt".equals(k)) {
            return R.color.status_error;
        }
        return R.color.status_warning;
    }

    public static boolean shouldPulse(String diseaseKey) {
        String k = DiseaseLabels.normalizeKey(diseaseKey);
        return "Rust".equals(k) || "BerryDisease".equals(k) || "Wilt".equals(k);
    }

    public static String riskLabel(Context context, String diseaseKey) {
        return context.getString(riskLabelRes(diseaseKey));
    }
}
