package com.example.coffeediseasesdetection;

import android.content.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** All coffee health conditions detected by the app (5 diseases + healthy). */
public final class DiseaseCatalog {

    public static final List<String> ALL_CONDITIONS = Collections.unmodifiableList(Arrays.asList(
            "Healthy", "Rust", "BerryDisease", "Wilt", "LeafMiner", "RootRot"
    ));

    private DiseaseCatalog() {}

    public static String scientificName(Context context, String key) {
        switch (DiseaseLabels.normalizeKey(key)) {
            case "Healthy":      return context.getString(R.string.disease_healthy_scientific);
            case "Rust":         return context.getString(R.string.disease_rust_scientific);
            case "BerryDisease": return context.getString(R.string.disease_berry_scientific);
            case "Wilt":         return context.getString(R.string.disease_wilt_scientific);
            case "LeafMiner":    return context.getString(R.string.disease_leaf_miner_scientific);
            case "RootRot":      return context.getString(R.string.disease_root_rot_scientific);
            default:             return "";
        }
    }

    public static boolean matchesQuery(Context context, String key, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.trim().toLowerCase();
        String name = DiseaseTextProvider.displayName(context, key).toLowerCase();
        String sci = scientificName(context, key).toLowerCase();
        String desc = DiseaseTextProvider.description(context, key).toLowerCase();
        return name.contains(q) || sci.contains(q) || desc.contains(q) || key.toLowerCase().contains(q);
    }
}
