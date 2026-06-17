package com.example.coffeediseasesdetection;

import com.google.android.material.navigation.NavigationView;

import java.util.Locale;
import java.util.Set;

/**
 * Role-based menu visibility (UI only). Does not change Firebase auth or data access rules.
 */
public final class RolePermissions {

    private RolePermissions() {}

    public static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) return "admin";
        String r = role.trim().toLowerCase(Locale.US);
        if ("super_admin".equals(r)) return "superadmin";
        if ("bwana_shamba".equals(r) || "bwana_mifugo".equals(r)) return "bwana_kilimo";
        if ("waziri".equals(r)) return "waziri_wa_kilimo";
        return r;
    }

    public static void applyAdminDrawerVisibility(NavigationView nav, String role) {
        if (nav == null) return;
        String r = normalizeRole(role);

        setVisible(nav, R.id.nav_admin_manage_farmers, canManageFarmers(r));
        setVisible(nav, R.id.nav_admin_register_user, canAddUsers(r));
        setVisible(nav, R.id.nav_admin_roles, canManageRoles(r));
        setVisible(nav, R.id.nav_admin_activity_log, canViewActivityLogs(r));
        setVisible(nav, R.id.nav_admin_statistics, canViewReports(r));
        setVisible(nav, R.id.nav_admin_reports, canViewReports(r));
        setVisible(nav, R.id.nav_admin_geo_map, canViewRegionalMap(r));
        setVisible(nav, R.id.nav_admin_scan_records, canViewScans(r));
        setVisible(nav, R.id.nav_admin_scan, canViewScans(r));
        setVisible(nav, R.id.nav_admin_upload, canViewScans(r));
        setVisible(nav, R.id.nav_admin_challenges, canViewChallenges(r));
        setVisible(nav, R.id.nav_admin_messages, canViewMessages(r));
        setVisible(nav, R.id.nav_admin_disease_database, canViewDiseases(r));
        setVisible(nav, R.id.nav_admin_recommendations, canViewDiseases(r));
        setVisible(nav, R.id.nav_admin_review_images, canViewScans(r));
    }

    private static void setVisible(NavigationView nav, int itemId, boolean visible) {
        if (nav.getMenu().findItem(itemId) != null) {
            nav.getMenu().findItem(itemId).setVisible(visible);
        }
    }

    private static boolean isAny(String role, String... roles) {
        Set<String> set = Set.of(roles);
        return set.contains(role);
    }

    public static boolean canManageFarmers(String role) {
        return isAny(role, "system_admin", "admin", "main", "superadmin");
    }

    public static boolean canAddUsers(String role) {
        return isAny(role, "system_admin", "it", "main", "superadmin");
    }

    public static boolean canManageRoles(String role) {
        return isAny(role, "system_admin", "it", "main", "superadmin");
    }

    public static boolean canViewActivityLogs(String role) {
        return isAny(role, "system_admin", "admin", "main", "superadmin", "it");
    }

    public static boolean canViewReports(String role) {
        return isAny(role, "system_admin", "admin", "main", "superadmin", "bwana_kilimo",
                "bwana_mifugo", "waziri", "waziri_wa_kilimo", "technician", "it");
    }

    public static boolean canViewRegionalMap(String role) {
        return isAny(role, "system_admin", "waziri", "waziri_wa_kilimo", "admin", "main", "superadmin");
    }

    public static boolean canViewScans(String role) {
        return isAny(role, "system_admin", "admin", "main", "superadmin", "technician",
                "bwana_kilimo", "bwana_shamba", "bwana_mifugo");
    }

    public static boolean canViewChallenges(String role) {
        return isAny(role, "system_admin", "admin", "main", "superadmin", "technician");
    }

    public static boolean canViewMessages(String role) {
        return isAny(role, "system_admin", "admin", "main", "superadmin", "technician");
    }

    public static boolean canViewDiseases(String role) {
        return isAny(role, "system_admin", "admin", "main", "superadmin", "technician",
                "bwana_kilimo", "bwana_shamba", "bwana_mifugo");
    }
}
