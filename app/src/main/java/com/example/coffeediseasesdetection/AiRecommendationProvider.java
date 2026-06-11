package com.example.coffeediseasesdetection;

import android.content.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AiRecommendationProvider {

    public static final class Recommendation {
        public final String medicines;
        public final String prevention;
        public final String treatment;
        public final int nextScanDays;
        public final List<Integer> reminderDays;

        public Recommendation(String medicines, String prevention, String treatment,
                              int nextScanDays, List<Integer> reminderDays) {
            this.medicines = medicines;
            this.prevention = prevention;
            this.treatment = treatment;
            this.nextScanDays = nextScanDays;
            this.reminderDays = reminderDays;
        }
    }

    private AiRecommendationProvider() {}

    public static Recommendation forDisease(Context context, String diseaseKey) {
        String key = DiseaseLabels.normalizeKey(diseaseKey);
        switch (key) {
            case "Rust":
                return new Recommendation(context.getString(R.string.rec_rust_medicine),
                        context.getString(R.string.rec_rust_prevention),
                        DiseaseTextProvider.treatment(context, key), 7, Arrays.asList(3, 7, 14));
            case "BerryDisease":
                return new Recommendation(context.getString(R.string.rec_berry_medicine),
                        context.getString(R.string.rec_berry_prevention),
                        DiseaseTextProvider.treatment(context, key), 7, Arrays.asList(3, 7, 14));
            case "Wilt":
                return new Recommendation(context.getString(R.string.rec_wilt_medicine),
                        context.getString(R.string.rec_wilt_prevention),
                        DiseaseTextProvider.treatment(context, key), 5, Arrays.asList(3, 7, 14));
            case "LeafMiner":
                return new Recommendation(context.getString(R.string.rec_leafminer_medicine),
                        context.getString(R.string.rec_leafminer_prevention),
                        DiseaseTextProvider.treatment(context, key), 7, Arrays.asList(3, 7, 14));
            case "RootRot":
                return new Recommendation(context.getString(R.string.rec_rootrot_medicine),
                        context.getString(R.string.rec_rootrot_prevention),
                        DiseaseTextProvider.treatment(context, key), 10, Arrays.asList(3, 7, 14));
            case "Healthy":
                return new Recommendation(context.getString(R.string.rec_healthy_medicine),
                        context.getString(R.string.rec_healthy_prevention),
                        DiseaseTextProvider.treatment(context, key), 14, Collections.singletonList(14));
            default:
                return new Recommendation(context.getString(R.string.rec_general_medicine),
                        context.getString(R.string.rec_general_prevention),
                        context.getString(R.string.rec_general_treatment), 7, Arrays.asList(3, 7, 14));
        }
    }

    public static String formatSummary(Context context, Recommendation rec) {
        return context.getString(R.string.rec_label_medicine) + ": " + rec.medicines + "\n\n"
                + context.getString(R.string.rec_label_prevention) + ": " + rec.prevention + "\n\n"
                + context.getString(R.string.rec_label_next_scan) + ": "
                + context.getString(R.string.rec_next_scan_days, rec.nextScanDays);
    }
}
