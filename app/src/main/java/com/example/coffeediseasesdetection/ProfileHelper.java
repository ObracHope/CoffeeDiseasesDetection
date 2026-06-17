package com.example.coffeediseasesdetection;

import android.content.Context;
import android.text.TextUtils;

/**
 * Consistent profile display: FirstName + LastName on top, role label below.
 */
public final class ProfileHelper {

    private ProfileHelper() {}

    public static String fullName(String firstName, String lastName, String fallbackName) {
        String f = firstName != null ? firstName.trim() : "";
        String l = lastName != null ? lastName.trim() : "";
        if (!f.isEmpty() || !l.isEmpty()) {
            return (f + " " + l).trim();
        }
        if (!TextUtils.isEmpty(fallbackName)) {
            return fallbackName.trim();
        }
        return "";
    }

    public static String roleLabel(Context context, String role) {
        if (context == null || TextUtils.isEmpty(role)) {
            return context != null ? context.getString(R.string.role_farmer) : "Farmer";
        }
        return AuthHelper.displayRoleLabel(context, role);
    }
}
