package com.example.coffeediseasesdetection;

import android.text.TextUtils;
import android.util.Patterns;

import java.util.Locale;

/** Client-side input sanitization and validation before Firebase calls. */
public final class InputValidator {

    private InputValidator() {}

    public static String sanitize(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    public static boolean isValidEmail(String email) {
        String e = sanitize(email);
        return !TextUtils.isEmpty(e) && Patterns.EMAIL_ADDRESS.matcher(e).matches();
    }

    public static boolean isValidPhoneDigits(String digits) {
        String d = sanitize(digits).replaceAll("\\D", "");
        return d.length() >= 9 && d.length() <= 12;
    }

    /** Firebase minimum 6; recommend letter + digit for stronger passwords. */
    public static String validatePassword(String password) {
        String p = password != null ? password : "";
        if (p.length() < 6) {
            return "Password must be at least 6 characters";
        }
        if (p.length() < 8) {
            return null;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : p.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            return "Use a stronger password with letters and numbers";
        }
        return null;
    }

    public static String validateLoginIdentifier(String identifier) {
        String id = sanitize(identifier);
        if (id.isEmpty()) return "Required";
        if (id.contains("@") && !isValidEmail(id)) return "Invalid email format";
        return null;
    }
}
