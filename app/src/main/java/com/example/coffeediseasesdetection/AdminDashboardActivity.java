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
import com.example.coffeediseasesdetection.weather.WeatherBannerHelper;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminDashboardActivity extends BaseActivity {

    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private ImageView ivProfile;
    private TextView tvAdminName;
    private TextView tvNotificationBadge;
    private AdminDashboardFragment homeFragment;
    private String adminDrawerRole = "admin";
    private String adminDrawerEmail = "";
    private String adminDrawerPhoto = "";
    private String adminDisplayName = "";
    private String adminFirstName = "";
    private String adminLastName = "";
    private final GreetingBannerHelper greetingHelper = new GreetingBannerHelper();
    private final WeatherBannerHelper weatherHelper = new WeatherBannerHelper();

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
        ScanImageUploadHelper.syncPendingUploads(this, auth.getCurrentUser().getUid());
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

        View profileBtn = findViewById(R.id.ivAdminProfile);
        if (profileBtn != null) {
            profileBtn.setClickable(true);
            profileBtn.setOnClickListener(this::showAdminProfilePopup);
        }
        View changePhotoBtn = findViewById(R.id.btnHeaderChangePhoto);
        if (changePhotoBtn != null) {
            changePhotoBtn.setOnClickListener(v -> startActivity(new Intent(this, UpdateProfile.class)));
        }
        View profileArrow = findViewById(R.id.ivProfileArrow);
        if (profileArrow != null) {
            profileArrow.setOnClickListener(this::showAdminProfilePopup);
        }

        if (drawerLayout != null) {
            setupNavigationDrawer(drawerLayout);
        }
        setupBottomNavigation();
        loadAdminProfile();
        loadNotifications();

        NavigationView navDrawer = findViewById(R.id.nav_drawer_admin);
        if (navDrawer != null) {
            setupDrawerHeader(navDrawer);
        }

        if (savedInstanceState == null) {
            showHomeFragment();
        } else {
            homeFragment = (AdminDashboardFragment) getSupportFragmentManager()
                    .findFragmentByTag("admin_home");
        }

        TextView greetingBanner = findViewById(R.id.tvGreetingBanner);
        if (greetingBanner != null) {
            greetingHelper.show(this, greetingBanner);
        }

        weatherHelper.attach(this, findViewById(R.id.tvHeaderWeather), findViewById(R.id.tvWeatherIcon));
        TextView tvWeather = findViewById(R.id.tvHeaderWeather);
        if (tvWeather != null) tvWeather.setSelected(true);
    }

    private void setupDrawerHeader(NavigationView navView) {
        if (navView == null) return;
        View headerView = navView.getHeaderView(0);
        if (headerView == null) return;

        View btnLogout = headerView.findViewById(R.id.btnPopupSignOut);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                DrawerLayout drawer = findViewById(R.id.drawer_layout_admin);
                if (drawer != null) drawer.closeDrawers();
                showLogoutDialog();
            });
        }

        View btnPhoto = headerView.findViewById(R.id.btnChangePhoto);
        if (btnPhoto != null) {
            btnPhoto.setOnClickListener(v -> {
                DrawerLayout drawer = findViewById(R.id.drawer_layout_admin);
                if (drawer != null) drawer.closeDrawers();
                startActivity(new Intent(this, UpdateProfile.class));
            });
        }
    }

    private void updateDrawerHeader(String firstName, String lastName, String fallbackName,
                                    String role, String email, String photoUrl) {
        NavigationView navView = findViewById(R.id.nav_drawer_admin);
        if (navView == null) return;
        View headerView = navView.getHeaderView(0);
        if (headerView == null) return;

        TextView tvName = headerView.findViewById(R.id.tvProfileName);
        TextView tvRole = headerView.findViewById(R.id.tvProfileRole);
        TextView tvEmail = headerView.findViewById(R.id.tvProfileEmail);
        ImageView ivPhoto = headerView.findViewById(R.id.ivProfilePhoto);

        String displayName = ProfileHelper.fullName(firstName, lastName, fallbackName);
        if (tvName != null) {
            tvName.setText(!displayName.isEmpty() ? displayName : getString(R.string.profile_name_placeholder));
        }
        if (tvRole != null) {
            tvRole.setText(ProfileHelper.roleLabel(this, role));
            tvRole.setVisibility(View.VISIBLE);
        }
        if (tvEmail != null && email != null && !email.isEmpty()) {
            tvEmail.setText(email);
        }
        if (ivPhoto != null) {
            ProfileAvatarHelper.load(ivPhoto, photoUrl);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        weatherHelper.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        weatherHelper.refresh();
        refreshAdminProfileFromCache();
    }

    private void refreshAdminProfileFromCache() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String name = prefs.getString(KEY_NAME, getString(R.string.admin_default_name));
        adminFirstName = prefs.getString(KEY_FIRST_NAME, "");
        adminLastName = prefs.getString(KEY_LAST_NAME, "");
        adminDrawerPhoto = prefs.getString(KEY_PHOTO, "");
        adminDrawerRole = prefs.getString(KEY_ROLE, "admin");
        adminDisplayName = ProfileHelper.fullName(adminFirstName, adminLastName, name);
        if (tvAdminName != null) tvAdminName.setText(adminDisplayName);
        ProfileAvatarHelper.load(ivProfile, adminDrawerPhoto);
        updateDrawerHeader(adminFirstName, adminLastName, name, adminDrawerRole, adminDrawerEmail, adminDrawerPhoto);
    }

    @Override
    protected void onDestroy() {
        weatherHelper.detach();
        greetingHelper.cancel();
        super.onDestroy();
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
            } else if (id == R.id.nav_admin_users) {
                startActivity(new Intent(this, AdminManageFarmersActivity.class));
            } else if (id == R.id.nav_admin_reports) {
                startActivity(new Intent(this, AdminReportsActivity.class));
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
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String role = AuthHelper.normalizeRole(prefs.getString(KEY_ROLE, "admin"));
            RolePermissions.applyAdminDrawerVisibility(navDrawer, role);
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
        adminFirstName = prefs.getString(KEY_FIRST_NAME, "");
        adminLastName = prefs.getString(KEY_LAST_NAME, "");
        adminDisplayName = ProfileHelper.fullName(adminFirstName, adminLastName, name);
        adminDrawerPhoto = prefs.getString(KEY_PHOTO, "");
        adminDrawerRole = prefs.getString(KEY_ROLE, "admin");
        adminDrawerEmail = user.getEmail() != null ? user.getEmail() : "";

        if (tvAdminName != null) tvAdminName.setText(adminDisplayName);
        ProfileAvatarHelper.load(ivProfile, adminDrawerPhoto);
        updateDrawerHeader(adminFirstName, adminLastName, name, adminDrawerRole, adminDrawerEmail, adminDrawerPhoto);

        mDatabase.child("users").child(user.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !isFinishing()) {
                    String n = snapshot.child("name").getValue(String.class);
                    String p = snapshot.child("photoUrl").getValue(String.class);
                    String r = snapshot.child("role").getValue(String.class);
                    String e = snapshot.child("email").getValue(String.class);
                    String f = snapshot.child("firstName").getValue(String.class);
                    String l = snapshot.child("lastName").getValue(String.class);
                    if (f != null) adminFirstName = f;
                    if (l != null) adminLastName = l;
                    adminDisplayName = ProfileHelper.fullName(adminFirstName, adminLastName, n);
                    if (tvAdminName != null) tvAdminName.setText(adminDisplayName);
                    if (p != null) adminDrawerPhoto = p;
                    if (r != null) adminDrawerRole = r;
                    if (e != null && !e.isEmpty()) adminDrawerEmail = e;
                    else if (user.getEmail() != null) adminDrawerEmail = user.getEmail();
                    ProfileAvatarHelper.load(ivProfile, adminDrawerPhoto);
                    saveUserCache(adminDrawerRole, n, p, f, l);
                    updateDrawerHeader(adminFirstName, adminLastName, n, adminDrawerRole, adminDrawerEmail, adminDrawerPhoto);
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
                if (isFinishing()) return;
                android.widget.Toast.makeText(AdminDashboardActivity.this,
                        R.string.admin_access_denied, android.widget.Toast.LENGTH_LONG).show();
                performLogout();
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

    private void showAdminProfilePopup(View anchor) {
        View popupView = getLayoutInflater().inflate(R.layout.layout_profile_popup, null);
        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(popupView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);

        TextView tvName = popupView.findViewById(R.id.tvPopupName);
        TextView tvEmail = popupView.findViewById(R.id.tvPopupEmail);
        ImageView ivPhoto = popupView.findViewById(R.id.ivPopupProfilePhoto);

        String name = ProfileHelper.fullName(adminFirstName, adminLastName, adminDisplayName);
        if (name.isEmpty()) {
            name = ProfileHelper.roleLabel(this, adminDrawerRole);
        }
        if (tvName != null) tvName.setText(name);
        if (tvEmail != null) tvEmail.setText(ProfileHelper.roleLabel(this, adminDrawerRole));
        if (ivPhoto != null && adminDrawerPhoto != null && !adminDrawerPhoto.isEmpty()) {
            Glide.with(this).load(adminDrawerPhoto).circleCrop()
                    .placeholder(R.drawable.placeholder_user).into(ivPhoto);
        }

        popupView.findViewById(R.id.btnPopupUploadPhoto).setOnClickListener(v -> {
            popupWindow.dismiss();
            startActivity(new Intent(this, UpdateProfile.class));
        });

        popupView.findViewById(R.id.btnPopupSignOut).setOnClickListener(v -> {
            popupWindow.dismiss();
            showLogoutDialog();
        });

        popupWindow.setElevation(20);
        popupWindow.showAsDropDown(anchor, -250, 0);
    }
}
