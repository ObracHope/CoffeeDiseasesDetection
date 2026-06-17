package com.example.coffeediseasesdetection;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Tanzania phone search: 255 / +255 / 0… / 6|7… — mirrors web panel-helpers.js */
public final class PhoneSearchHelper {

    private PhoneSearchHelper() {
    }

    public static String digitsOnly(String value) {
        if (value == null) return "";
        return value.replaceAll("\\D", "");
    }

    private static void addTzVariants(String digits, Set<String> set) {
        if (digits == null || digits.isEmpty()) return;
        set.add(digits);

        if (digits.startsWith("255") && digits.length() > 3) {
            String local = digits.substring(3);
            set.add(local);
            set.add("0" + local);
        }
        if (digits.startsWith("0") && digits.length() > 1) {
            String rest = digits.substring(1);
            set.add(rest);
            set.add("255" + rest);
        }
        if (!digits.startsWith("255") && !digits.startsWith("0")
                && (digits.startsWith("6") || digits.startsWith("7"))) {
            set.add("255" + digits);
            set.add("0" + digits);
        }
    }

    public static boolean isPhoneLikeSearchTerm(String term) {
        if (term == null) return false;
        String t = term.trim();
        if (t.isEmpty()) return false;
        String digits = digitsOnly(t);
        if (digits.length() < 3) return false;
        return t.matches("^[\\d+\\s().-]+$");
    }

    public static boolean phoneMatchesSearchTerm(String phone, String term) {
        String phoneD = digitsOnly(phone);
        String termD = digitsOnly(term);
        if (phoneD.isEmpty() || termD.isEmpty()) return false;

        Set<String> phoneVars = new HashSet<>();
        Set<String> termVars = new HashSet<>();
        addTzVariants(phoneD, phoneVars);
        addTzVariants(termD, termVars);

        for (String p : phoneVars) {
            for (String t : termVars) {
                if (p.contains(t) || t.contains(p)) return true;
            }
        }
        return false;
    }

    public static boolean recordMatchesSearch(String[] fields, String term) {
        if (term == null || term.trim().isEmpty()) return true;
        String q = term.trim();
        String lower = q.toLowerCase(Locale.US);

        StringBuilder hay = new StringBuilder();
        for (String f : fields) {
            if (f != null) hay.append(f).append(' ');
        }
        if (hay.toString().toLowerCase(Locale.US).contains(lower)) return true;

        if (isPhoneLikeSearchTerm(q)) {
            for (String f : fields) {
                if (f != null && phoneMatchesSearchTerm(f, q)) return true;
            }
        }
        return false;
    }

    public static boolean mapMatchesSearch(Map<String, Object> map, String term, String... keys) {
        if (term == null || term.trim().isEmpty()) return true;
        String[] values = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Object v = map.get(keys[i]);
            values[i] = v != null ? String.valueOf(v) : "";
        }
        return recordMatchesSearch(values, term);
    }
}
