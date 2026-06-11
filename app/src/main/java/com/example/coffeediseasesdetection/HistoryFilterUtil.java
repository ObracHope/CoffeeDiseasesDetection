package com.example.coffeediseasesdetection;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Filter scan history by disease name/key or date text. */
public final class HistoryFilterUtil {

    private HistoryFilterUtil() {}

    public static List<Map<String, Object>> filter(List<Map<String, Object>> source, String query) {
        if (source == null) return new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase(Locale.getDefault()) : "";
        if (q.isEmpty()) return new ArrayList<>(source);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : source) {
            if (matchesDisease(item, q) || matchesDate(item, q)) {
                out.add(item);
            }
        }
        return out;
    }

    private static boolean matchesDisease(Map<String, Object> item, String q) {
        String name = item.get("diseaseName") != null ? item.get("diseaseName").toString() : "";
        String disease = item.get("disease") != null ? item.get("disease").toString() : "";
        String key = DiseaseLabels.normalizeKey(disease);
        String english = DiseaseLabels.englishName(key);

        return contains(name, q)
                || contains(disease, q)
                || contains(key, q)
                || contains(english, q);
    }

    private static boolean matchesDate(Map<String, Object> item, String q) {
        long ms = timestampMillis(item);
        if (ms <= 0) return false;
        Date date = new Date(ms);
        Locale loc = Locale.getDefault();
        String[] patterns = {
                "dd MMM yyyy",
                "dd MMM yyyy, HH:mm",
                "dd/MM/yyyy",
                "yyyy-MM-dd",
                "MMM yyyy",
                "dd MMM",
                "yyyy"
        };
        for (String pattern : patterns) {
            String formatted = new SimpleDateFormat(pattern, loc).format(date).toLowerCase(loc);
            if (formatted.contains(q)) return true;
        }
        return false;
    }

    private static long timestampMillis(Map<String, Object> row) {
        Object ts = row.get("timestamp");
        if (ts instanceof Timestamp) return ((Timestamp) ts).toDate().getTime();
        if (ts instanceof Long) return (Long) ts;
        return 0L;
    }

    private static boolean contains(String value, String q) {
        return value != null && !value.isEmpty()
                && value.toLowerCase(Locale.getDefault()).contains(q);
    }
}
