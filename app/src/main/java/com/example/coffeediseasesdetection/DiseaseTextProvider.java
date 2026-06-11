package com.example.coffeediseasesdetection;

import android.content.Context;

/** Localized disease descriptions, symptoms, and treatments. */
public final class DiseaseTextProvider {

    private DiseaseTextProvider() {}

    public static String displayName(Context context, String key) {
        switch (DiseaseLabels.normalizeKey(key)) {
            case "Healthy":      return context.getString(R.string.disease_healthy_name);
            case "Rust":         return context.getString(R.string.disease_rust_name);
            case "BerryDisease": return context.getString(R.string.disease_berry_name);
            case "Wilt":         return context.getString(R.string.disease_wilt_name);
            case "LeafMiner":    return context.getString(R.string.disease_leaf_miner_name);
            case "RootRot":      return context.getString(R.string.disease_root_rot_name);
            case "IsNotCoffee":  return context.getString(R.string.not_coffee_title);
            case "Uncertain":    return context.getString(R.string.uncertain_result_title);
            case "Error":        return context.getString(R.string.error_title);
            default:             return key != null ? key : context.getString(R.string.unknown);
        }
    }

    public static String description(Context context, String key) {
        switch (DiseaseLabels.normalizeKey(key)) {
            case "Healthy":      return context.getString(R.string.disease_healthy_desc);
            case "Rust":         return context.getString(R.string.disease_rust_desc);
            case "BerryDisease": return context.getString(R.string.disease_berry_desc);
            case "Wilt":         return context.getString(R.string.disease_wilt_desc);
            case "LeafMiner":    return context.getString(R.string.disease_leaf_miner_desc);
            case "RootRot":      return context.getString(R.string.disease_root_rot_desc);
            case "IsNotCoffee":  return context.getString(R.string.not_coffee_desc);
            case "Uncertain":    return context.getString(R.string.uncertain_desc);
            default:             return context.getString(R.string.not_coffee_desc);
        }
    }

    public static String symptoms(Context context, String key) {
        switch (DiseaseLabels.normalizeKey(key)) {
            case "Healthy":      return context.getString(R.string.disease_healthy_symptoms);
            case "Rust":         return context.getString(R.string.disease_rust_symptoms);
            case "BerryDisease": return context.getString(R.string.disease_berry_symptoms);
            case "Wilt":         return context.getString(R.string.disease_wilt_symptoms);
            case "LeafMiner":    return context.getString(R.string.disease_leaf_miner_symptoms);
            case "RootRot":      return context.getString(R.string.disease_root_rot_symptoms);
            case "Uncertain":    return context.getString(R.string.uncertain_symptoms);
            default:             return context.getString(R.string.not_coffee_symptoms);
        }
    }

    public static String treatment(Context context, String key) {
        switch (DiseaseLabels.normalizeKey(key)) {
            case "Healthy":      return context.getString(R.string.disease_healthy_treatment);
            case "Rust":         return context.getString(R.string.disease_rust_treatment);
            case "BerryDisease": return context.getString(R.string.disease_berry_treatment);
            case "Wilt":         return context.getString(R.string.disease_wilt_treatment);
            case "LeafMiner":    return context.getString(R.string.disease_leaf_miner_treatment);
            case "RootRot":      return context.getString(R.string.disease_root_rot_treatment);
            case "Uncertain":    return context.getString(R.string.uncertain_treatment);
            default:             return context.getString(R.string.not_coffee_treatment);
        }
    }
}
