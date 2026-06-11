package com.example.coffeediseasesdetection;

import android.content.Context;

/** Three-step scan result: coffee? → healthy? → which disease? */
public final class DetectionResult {

    public final boolean isCoffee;
    public final boolean isHealthy;
    public final String diseaseKey;
    public final float confidence;
    public final String description;
    public final String symptoms;
    public final String treatment;

    private DetectionResult(boolean isCoffee, boolean isHealthy, String diseaseKey,
                            float confidence, String description, String symptoms, String treatment) {
        this.isCoffee = isCoffee;
        this.isHealthy = isHealthy;
        this.diseaseKey = diseaseKey;
        this.confidence = confidence;
        this.description = description;
        this.symptoms = symptoms;
        this.treatment = treatment;
    }

    public static DetectionResult notCoffee(Context ctx, float confidence) {
        return new DetectionResult(
                false, false, DiseaseDetector.NOT_COFFEE_LABEL, confidence,
                DiseaseTextProvider.description(ctx, "IsNotCoffee"),
                DiseaseTextProvider.symptoms(ctx, "IsNotCoffee"),
                DiseaseTextProvider.treatment(ctx, "IsNotCoffee")
        );
    }

    public static DetectionResult healthyCoffee(Context ctx, float confidence) {
        return new DetectionResult(
                true, true, "Healthy", confidence,
                DiseaseTextProvider.description(ctx, "Healthy"),
                DiseaseTextProvider.symptoms(ctx, "Healthy"),
                DiseaseTextProvider.treatment(ctx, "Healthy")
        );
    }

    public static DetectionResult diseasedCoffee(Context ctx, String diseaseKey, float confidence) {
        return new DetectionResult(
                true, false, diseaseKey, confidence,
                DiseaseTextProvider.description(ctx, diseaseKey),
                DiseaseTextProvider.symptoms(ctx, diseaseKey),
                DiseaseTextProvider.treatment(ctx, diseaseKey)
        );
    }

    public static DetectionResult uncertain(Context ctx, float confidence) {
        return new DetectionResult(
                true, false, "Uncertain", confidence,
                DiseaseTextProvider.description(ctx, "Uncertain"),
                DiseaseTextProvider.symptoms(ctx, "Uncertain"),
                DiseaseTextProvider.treatment(ctx, "Uncertain")
        );
    }

    public static DetectionResult error(String message) {
        return new DetectionResult(false, false, "Error", 0f, message, "", "");
    }

    public String step1Text(Context ctx) {
        return isCoffee
                ? ctx.getString(R.string.step1_coffee_yes)
                : ctx.getString(R.string.step1_coffee_no);
    }

    public String step2Text(Context ctx) {
        if (!isCoffee) return "";
        if ("Uncertain".equals(diseaseKey)) {
            return ctx.getString(R.string.step2_uncertain);
        }
        return isHealthy
                ? ctx.getString(R.string.step2_healthy)
                : ctx.getString(R.string.step2_diseased);
    }

    public String step3Text(Context ctx) {
        if (!isCoffee) return "";
        if (isHealthy) return ctx.getString(R.string.step3_no_disease);
        if ("Uncertain".equals(diseaseKey)) return ctx.getString(R.string.step3_uncertain);
        return ctx.getString(R.string.step3_disease,
                DiseaseTextProvider.displayName(ctx, diseaseKey));
    }

    public String mainTitle(Context ctx) {
        return DiseaseTextProvider.displayName(ctx, diseaseKey);
    }

    public boolean isActionableCoffeeScan() {
        return isCoffee && !"Uncertain".equals(diseaseKey) && !"Error".equals(diseaseKey);
    }
}
