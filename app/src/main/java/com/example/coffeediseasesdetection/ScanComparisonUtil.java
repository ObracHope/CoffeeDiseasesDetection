package com.example.coffeediseasesdetection;

import java.util.Locale;
import java.util.Map;

public final class ScanComparisonUtil {

    public enum ProgressStatus { IMPROVING, WORSENING, STABLE, RECOVERED, FIRST_SCAN, NOT_COMPARABLE }

    public static final class ComparisonResult {
        public final ProgressStatus status;
        public final String summary;
        public final float confidenceDelta;

        public ComparisonResult(ProgressStatus status, String summary, float confidenceDelta) {
            this.status = status;
            this.summary = summary;
            this.confidenceDelta = confidenceDelta;
        }
    }

    private ScanComparisonUtil() {}

    public static ComparisonResult compare(Map<String, Object> previous, Map<String, Object> current) {
        if (previous == null || current == null) {
            return new ComparisonResult(ProgressStatus.FIRST_SCAN, "", 0f);
        }
        boolean prevHealthy = Boolean.TRUE.equals(previous.get("isHealthy"));
        boolean currHealthy = Boolean.TRUE.equals(current.get("isHealthy"));
        float prevConf = toFloat(previous.get("confidence"));
        float currConf = toFloat(current.get("confidence"));
        float delta = currConf - prevConf;
        String prevD = disease(previous);
        String currD = disease(current);

        if (prevHealthy && currHealthy) {
            return new ComparisonResult(ProgressStatus.STABLE,
                    "Kahawa bado ni afya. Endelea utunzaji wa kawaida.", delta);
        }
        if (!prevHealthy && currHealthy) {
            return new ComparisonResult(ProgressStatus.RECOVERED,
                    "Kahawa imeanza kupona! Ugonjwa haupo tena kwenye scan mpya.", delta);
        }
        if (prevHealthy && !currHealthy) {
            return new ComparisonResult(ProgressStatus.WORSENING,
                    "Ugonjwa umeonekana kwa mara ya kwanza au umeongezeka.", delta);
        }
        if (!prevD.equals(currD)) {
            return new ComparisonResult(ProgressStatus.NOT_COMPARABLE,
                    String.format(Locale.getDefault(), "Ugonjwa: %s → %s", prevD, currD), delta);
        }
        if (currConf < prevConf - 5f) {
            return new ComparisonResult(ProgressStatus.IMPROVING,
                    "Dalili zinaonekana kupungua. Endelea matibabu.", delta);
        }
        if (currConf > prevConf + 5f) {
            return new ComparisonResult(ProgressStatus.WORSENING,
                    "Dalili zinaonekana kuongezeka. Scan tena baada ya matibabu.", delta);
        }
        return new ComparisonResult(ProgressStatus.STABLE,
                "Hali imesalia sawa. Endelea matibabu na scan tena.", delta);
    }

    private static String disease(Map<String, Object> scan) {
        Object d = scan.get("disease");
        if (d == null) d = scan.get("diseaseName");
        return DiseaseLabels.normalizeKey(d != null ? d.toString() : "Unknown");
    }

    private static float toFloat(Object o) {
        if (o instanceof Number) return ((Number) o).floatValue();
        if (o instanceof String) {
            try {
                return Float.parseFloat(((String) o).replace("%", "").trim());
            } catch (Exception ignored) { }
        }
        return 0f;
    }
}
