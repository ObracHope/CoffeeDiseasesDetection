package com.example.coffeediseasesdetection;

import java.util.Locale;

/** Canonical disease keys and display names for UI + analytics. */
public final class DiseaseLabels {

    public static final String NOT_COFFEE_TITLE = "Samahani hii si picha ya kahawa";
    public static final String NOT_COFFEE_DESC =
            "Hii sio picha ya kahawa. Tafadhali tumia picha ya jani, maua, au beri za kahawa.";

    public static final String COFFEE_YES = "Hii ni picha ya kahawa";
    public static final String COFFEE_NO = "Hii si picha ya kahawa";
    public static final String HEALTHY_YES = "Kahawa yenye afya — hakuna ugonjwa";
    public static final String HEALTHY_NO = "Kahawa iliyo na ugonjwa";

    private DiseaseLabels() {}

    public static String englishName(String key) {
        if (key == null) return "—";
        switch (key) {
            case "Healthy":      return "Healthy Coffee";
            case "Rust":         return "Coffee Leaf Rust";
            case "BerryDisease": return "Coffee Berry Disease";
            case "Wilt":         return "Coffee Wilt Disease";
            case "LeafMiner":    return "Leaf Miner";
            case "RootRot":      return "Root Rot";
            case "IsNotCoffee":  return NOT_COFFEE_TITLE;
            case "Uncertain":    return "Uncertain Result";
            case "Error":        return "Error";
            default:             return key;
        }
    }

    public static String friendlyName(String key) {
        return englishName(key);
    }

    public static String normalizeKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Unknown";
        String s = raw.trim();
        if (s.equals("Healthy") || s.contains("Healthy") || s.contains("Afya")) return "Healthy";
        if (s.equals("Rust") || s.contains("Rust") || s.contains("Kutu")) return "Rust";
        if (s.equals("BerryDisease") || s.contains("Berry") || s.contains("Beri")) return "BerryDisease";
        if (s.equals("Wilt") || s.contains("Wilt") || s.contains("Mnyauko")) return "Wilt";
        if (s.equals("LeafMiner") || s.contains("Leaf Miner") || s.contains("Mchimbaji")) return "LeafMiner";
        if (s.equals("RootRot") || s.contains("Root Rot") || s.contains("Mizizi")) return "RootRot";
        if (s.equals("IsNotCoffee") || s.contains("Not Coffee") || s.contains("Si Picha")
                || s.contains("Samahani")) return "IsNotCoffee";
        if (s.equals("Uncertain") || s.contains("Uncertain")) return "Uncertain";
        if (isPredictionFailure(s)) return "Error";
        if (s.equals("Error")) return "Error";
        return s;
    }

    private static boolean isPredictionFailure(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase(Locale.US);
        return lower.contains("prediction error")
                || lower.contains("tensorflow")
                || lower.contains("cannot copy")
                || lower.startsWith("error:");
    }

    public static boolean isValidScan(String key) {
        String k = normalizeKey(key);
        return !"IsNotCoffee".equals(k) && !"Error".equals(k) && !"Uncertain".equals(k)
                && !"Unknown".equals(k);
    }

    public static boolean isDiseaseFound(String key) {
        String k = normalizeKey(key);
        return isValidScan(k) && !"Healthy".equals(k);
    }
}
