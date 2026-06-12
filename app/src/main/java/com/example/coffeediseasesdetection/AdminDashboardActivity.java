package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminDashboardActivity extends BaseActivity {

    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private ImageView ivProfile;
    private TextView tvAdminName;
    private TextView tvNotificationBadge;
    private AdminDashboardFragment homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_dashboard_modern);
        verifyAdminRole();
        mDatabase = FirebaseDatabase.getInstance(AuthHelper.RTDB_URL).getReference();

        ivProfile = findViewById(R.id.ivAdminProfile);
        tvAdminName = findViewById(R.id.tvAdminName);
        tvNotificationBadge = findViewById(R.id.tvNotificationCount);

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout_admin);
        View menuBtn = findViewById(R.id.ivMenuAdmin);
        if (menuBtn != null && drawerLayout != null) {
            menuBtn.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        View searchBtn = findViewById(R.id.btnHeaderSearch);
        if (searchBtn != null) {
            searchBtn.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawers();
                startActivity(new Intent(this, AdminGlobalSearchActivity.class));
            });
        }

        View notifBtn = findViewById(R.id.btnHeaderNotification);
        if (notifBtn != null) {
            notifBtn.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawers();
                startActivity(new Intent(this, AdminBroadcastNotificationsActivity.class));
            });
        }

        if (drawerLayout != null) {
            setupNavigationDrawer(drawerLayout);
        }
        setupBottomNavigation();
        loadAdminProfile();
        loadNotifications();

        if (savedInstanceState == null) {
            showHomeFragment();
        } else {
            homeFragment = (AdminDashboardFragment) getSupportFragmentManager()
                    .findFragmentByTag("admin_home");
        }
    }

    private void showHomeFragment() {
        homeFragment = new AdminDashboardFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, homeFragment, "admin_home")
                .commit();
    }

    private void setupBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_admin_home) {
                showHomeFragment();
            } else if (id == R.id.nav_admin_scan) {
                startActivity(new Intent(this, CameraActivity.class));
            } else if (id == R.id.nav_admin_users) {
                startActivity(new Intent(this, AdminManageFarmersActivity.class));
            } else if (id == R.id.nav_admin_reports) {
                startActivity(new Intent(this, AdminReportsActivity.class));
            } else if (id == R.id.nav_admin_settings) {
                startActivity(new Intent(this, AdminInternationalSettingsActivity.class));
            }
            return true;
        });
    }

    private void setupNavigationDrawer(DrawerLayout drawerLayout) {
        NavigationView navDrawer = findViewById(R.id.nav_drawer_admin);
        if (navDrawer != null) {
            navDrawer.setNavigationItemSelectedListener(item -> {
                drawerLayout.closeDrawers();
                int id = item.getItemId();
                if (id == R.id.nav_admin_logout) {
                    showLogoutDialog();
                } else {
                    handleDrawerNavigation(id);
                }
                return true;
            });
        }
    }

    private void handleDrawerNavigation(int id) {
        Intent intent = null;
        if (id == R.id.nav_admin_scan) intent = new Intent(this, CameraActivity.class);
        else if (id == R.id.nav_admin_upload) intent = new Intent(this, UploadImageActivity.class);
        else if (id == R.id.nav_admin_manage_farmers) intent = new Intent(this, AdminManageFarmersActivity.class);
        else if (id == R.id.nav_admin_register_user) intent = new Intent(this, AdminRegisterUserActivity.class);
        else if (id == R.id.nav_admin_challenges) intent = new Intent(this, AdminChallengesActivity.class);
        else if (id == R.id.nav_admin_statistics) intent = new Intent(this, AdminStatisticsActivity.class);
        else if (id == R.id.nav_admin_reports) intent = new Intent(this, AdminReportsActivity.class);
        else if (id == R.id.nav_admin_scan_records) intent = new Intent(this, AdminScansListActivity.class);
        else if (id == R.id.nav_admin_geo_map) intent = new Intent(this, AdminGeoMapActivity.class);
        else if (id == R.id.nav_admin_review_images) intent = new Intent(this, AdminReviewImagesActivity.class);
        else if (id == R.id.nav_admin_activity_log) intent = new Intent(this, AdminActivityLogActivity.class);
        else if (id == R.id.nav_admin_messages) intent = new Intent(this, AdminMessagesActivity.class);
        else if (id == R.id.nav_admin_notifications) intent = new Intent(this, AdminBroadcastNotificationsActivity.class);
        else if (id == R.id.nav_admin_roles) intent = new Intent(this, AdminRolesActivity.class);
        else if (id == R.id.nav_admin_disease_database) intent = new Intent(this, AdminDiseaseDatabaseActivity.class);
        else if (id == R.id.nav_admin_recommendations) intent = new Intent(this, AdminRecommendationsActivity.class);
        else if (id == R.id.nav_admin_settings) intent = new Intent(this, AdminInternationalSettingsActivity.class);

        if (intent != null) startActivity(intent);
    }

    private void loadAdminProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String name = prefs.getString(KEY_NAME, getString(R.string.admin_default_name));
        String photo = prefs.getString(KEY_PHOTO, "");

        if (tvAdminName != null) tvAdminName.setText(name);
        if (ivProfile != null && !photo.isEmpty()) {
            Glide.with(this).load(photo).placeholder(R.drawable.placeholder_user).into(ivProfile);
        }

        mDatabase.child("users").child(user.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !isFinishing()) {
                    String n = snapshot.child("name").getValue(String.class);
                    String p = snapshot.child("photoUrl").getValue(String.class);
                    if (tvAdminName != null && n != null) tvAdminName.setText(n);
                    if (ivProfile != null && p != null && !p.isEmpty()) {
                        Glide.with(AdminDashboardActivity.this).load(p)
                                .placeholder(R.drawable.placeholder_user).into(ivProfile);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void loadNotifications() {
        mDatabase.child("farmer_challenges").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int pending = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    if ("pending".equals(child.child("status").getValue(String.class))) pending++;
                }
                if (tvNotificationBadge != null) {
                    tvNotificationBadge.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);
                    tvNotificationBadge.setText(String.valueOf(pending));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void verifyAdminRole() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        AuthHelper.fetchUserRole(user.getUid(), new AuthHelper.RoleCallback() {
            @Override
            public void onRole(String role) {
                if (isFinishing()) return;
                if (!AuthHelper.isAdminRole(role)) {
                    android.widget.Toast.makeText(AdminDashboardActivity.this,
                            R.string.admin_access_denied, android.widget.Toast.LENGTH_LONG).show();
                    startActivity(new Intent(AdminDashboardActivity.this, FarmerDashboardActivity.class));
                    finish();
                }
            }

            @Override
            public void onError(Exception e) {
                // Allow access if profile fetch fails — data hub will use RTDB fallback
            }
        });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> performLogout())
                .setNegativeButton(R.string.no, null)
                .show();
    }
}
