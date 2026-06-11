package com.example.coffeediseasesdetection;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Secondary Firebase Auth instance so admin can create users without signing out.
 */
public final class AdminAuthHelper {

    private static final String SECONDARY_APP = "AdminUserCreation";

    private AdminAuthHelper() {
    }

    public static FirebaseAuth secondaryAuth() {
        FirebaseApp defaultApp = FirebaseApp.getInstance();
        FirebaseApp secondary;
        try {
            secondary = FirebaseApp.getInstance(SECONDARY_APP);
        } catch (IllegalStateException e) {
            secondary = FirebaseApp.initializeApp(
                    defaultApp.getApplicationContext(),
                    defaultApp.getOptions(),
                    SECONDARY_APP);
        }
        return FirebaseAuth.getInstance(secondary);
    }
}
