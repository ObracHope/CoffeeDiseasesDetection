package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.example.coffeediseasesdetection.weather.WeatherBannerHelper;
import com.google.firebase.firestore.ListenerRegistration;

public class FarmerDashboardActivity extends BaseActivity implements FarmerScanStatsHost {

    public static final String PREFS_NOTIFICATION = "notification_prefs";
    public static final String KEY_COUNT = "unread_count";
    private DrawerLayout drawerLayout;
    private TextView tvNotificationCount;
    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private String userName = "Farmer", userEmail = "", userPhotoUrl = "";
    private String userFirstName = "", userLastName = "", userRole = "farmer";
    private static final String PREFS_REPLY_STATE = "challenge_reply_state";

    private final GreetingBannerHelper greetingHelper = new GreetingBannerHelper();
    private final WeatherBannerHelper weatherHelper = new WeatherBannerHelper();
    private ListenerRegistration scanStatsRegistration;
    private FarmerScanStatsHost.StatsCallback scanStatsCallback;
    private ScanHistoryLoader.DashboardStats lastStats;
    private boolean locationGateShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_farmer_dashboard);
        ScanImageUploadHelper.syncPendingUploads(this, auth.getCurrentUser().getUid());
        // Initialize RTDB reference
        mDatabase = AuthHelper.usersRtdb();
        
        drawerLayout = findViewById(R.id.drawer_layout);
        tvNotificationCount = findViewById(R.id.tvNotificationCount);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        View menuBtn = findViewById(R.id.ivMenu);
        if (menuBtn != null && drawerLayout != null) {
            menuBtn.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        View profileBtn = findViewById(R.id.ivHeaderProfile);
        if (profileBtn != null) {
            profileBtn.setOnClickListener(this::showProfilePopup);
        }
        View changePhotoBtn = findViewById(R.id.btnHeaderChangePhoto);
        if (changePhotoBtn != null) {
            changePhotoBtn.setOnClickListener(v -> startActivity(new Intent(this, UpdateProfile.class)));
        }
        View profileArrow = findViewById(R.id.ivProfileArrow);
        if (profileArrow != null) {
            profileArrow.setOnClickListener(this::showProfilePopup);
        }

        View notifBtn = findViewById(R.id.btnHeaderNotification);
        if (notifBtn != null) {
            notifBtn.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NOTIFICATION, MODE_PRIVATE).edit().putInt(KEY_COUNT, 0).apply();
            updateNotificationBadge();
            startActivity(new Intent(this, NotificationsActivity.class));
            });
        }

        updateNotificationBadge();

        View fabInfo = findViewById(R.id.fabInfo);
        if (fabInfo != null) {
            fabInfo.setOnClickListener(v -> startActivity(new Intent(this, HelpTipsActivity.class)));
        }

        NavigationView navDrawer = findViewById(R.id.nav_drawer);
        if (navDrawer != null) {
            setupDrawerHeader(navDrawer);
            navDrawer.setNavigationItemSelectedListener(item -> {
            if (drawerLayout != null) drawerLayout.closeDrawers();
            int id = item.getItemId();
            if (id == R.id.nav_disease_history) {
                startActivity(new Intent(this, HistoryActivity.class));
            } else if (id == R.id.nav_treatment_guide) {
                startActivity(new Intent(this, TreatmentGuideActivity.class));
            } else if (id == R.id.nav_report_challenge) {
                startActivity(new Intent(this, ReportChallengeActivity.class));
            } else if (id == R.id.nav_farming_tips) {
                startActivity(new Intent(this, HelpTipsActivity.class));
            } else if (id == R.id.nav_help_support) {
                startActivity(new Intent(this, HelpSupportActivity.class));
            } else if (id == R.id.nav_about_us) {
                startActivity(new Intent(this, AboutUsActivity.class));
            } else if (id == R.id.nav_my_analytics) {
                startActivity(new Intent(this, FarmerAnalyticsActivity.class));
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
            });
        }

        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_scan) {
                selectedFragment = new ScanFragment();
            } else if (itemId == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
            });
        }

        if (savedInstanceState == null && bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        loadUserInfo();
        observeAdminReplies();
        showGreetingBanner();
        startScanStatsListener();
        weatherHelper.attach(this, findViewById(R.id.tvHeaderWeather), findViewById(R.id.tvWeatherIcon));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        weatherHelper.onRequestPermissionsResult(requestCode, grantResults);
    }

    private void showGreetingBanner() {
        TextView banner = findViewById(R.id.tvGreetingBanner);
        if (banner != null) {
            greetingHelper.show(this, banner);
        }
    }

    @Override
    public void setScanStatsCallback(StatsCallback callback) {
        this.scanStatsCallback = callback;
        if (callback != null && lastStats != null) {
            callback.onStatsUpdated(lastStats);
        }
    }

    @Override
    public void startScanStatsListener() {
        stopScanStatsListener();
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) return;

        scanStatsRegistration = ScanHistoryLoader.listen(this, user, new ScanHistoryLoader.Callback() {
            @Override
            public void onLoaded(java.util.List<java.util.Map<String, Object>> scans) {
                if (isFinishing()) return;
                lastStats = ScanHistoryLoader.computeStats(scans);
                if (scanStatsCallback != null) {
                    scanStatsCallback.onStatsUpdated(lastStats);
                }
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing()) return;
                ScanHistoryLoader.loadOnce(FarmerDashboardActivity.this, user, new ScanHistoryLoader.Callback() {
                    @Override
                    public void onLoaded(java.util.List<java.util.Map<String, Object>> scans) {
                        if (isFinishing()) return;
                        lastStats = ScanHistoryLoader.computeStats(scans);
                        if (scanStatsCallback != null) {
                            scanStatsCallback.onStatsUpdated(lastStats);
                        }
                    }

                    @Override
                    public void onError(Exception ex) {
                        // no-op
                    }
                });
            }
        });
    }

    @Override
    public void stopScanStatsListener() {
        if (scanStatsRegistration != null) {
            scanStatsRegistration.remove();
            scanStatsRegistration = null;
        }
    }

    private void loadUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        userEmail = user.getEmail();
        
        // Load from Cache First for instant feel
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userName = prefs.getString(KEY_NAME, "Farmer");
        userFirstName = prefs.getString(KEY_FIRST_NAME, "");
        userLastName = prefs.getString(KEY_LAST_NAME, "");
        userRole = prefs.getString(KEY_ROLE, "farmer");
        userPhotoUrl = prefs.getString(KEY_PHOTO, "");
        if ((userPhotoUrl == null || userPhotoUrl.isEmpty()) && user.getPhotoUrl() != null) {
            userPhotoUrl = user.getPhotoUrl().toString();
        }

        updateUIWithUserInfo();

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .addSnapshotListener((doc, e) -> {
                    if (doc != null && doc.exists() && !isFinishing()) {
                        String name = doc.getString("name");
                        String photo = doc.getString("photoUrl");
                        String fName = doc.getString("firstName");
                        String lName = doc.getString("lastName");
                        String role = doc.getString("role");
                        if (name != null) userName = name;
                        if (photo != null) userPhotoUrl = photo;
                        if (fName != null) userFirstName = fName;
                        if (lName != null) userLastName = lName;
                        if (role != null) userRole = role;
                        saveUserCache(role, name, photo, fName, lName);
                        updateUIWithUserInfo();
                        maybeRequireLocation(doc);
                    }
                });

        mDatabase.child(user.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !isFinishing()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String photo = snapshot.child("photoUrl").getValue(String.class);
                    String fName = snapshot.child("firstName").getValue(String.class);
                    String lName = snapshot.child("lastName").getValue(String.class);
                    String role = snapshot.child("role").getValue(String.class);

                    saveUserCache(role, name, photo, fName, lName);

                    userName = name != null ? name : userName;
                    userEmail = email != null ? email : userEmail;
                    userPhotoUrl = photo != null ? photo : userPhotoUrl;
                    if (fName != null) userFirstName = fName;
                    if (lName != null) userLastName = lName;
                    if (role != null) userRole = role;

                    updateUIWithUserInfo();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Log or handle error
            }
        });
    }

    private void updateUIWithUserInfo() {
        if (isFinishing() || isDestroyed()) return;

        ImageView ivHeaderProfile = findViewById(R.id.ivHeaderProfile);
        NavigationView navView = findViewById(R.id.nav_drawer);
        View headerView = navView != null ? navView.getHeaderView(0) : null;

        if (ivHeaderProfile != null) {
            ProfileAvatarHelper.load(ivHeaderProfile, userPhotoUrl);
        }

        if (headerView != null) {
            TextView tvDName = headerView.findViewById(R.id.tvProfileName);
            TextView tvDRole = headerView.findViewById(R.id.tvProfileRole);
            TextView tvDEmail = headerView.findViewById(R.id.tvProfileEmail);
            ImageView ivDPhoto = headerView.findViewById(R.id.ivProfilePhoto);

            String displayName = ProfileHelper.fullName(userFirstName, userLastName, userName);
            if (tvDName != null) {
                tvDName.setText(!displayName.isEmpty() ? displayName : getString(R.string.profile_name_placeholder));
            }
            if (tvDRole != null) {
                tvDRole.setText(ProfileHelper.roleLabel(this, userRole));
                tvDRole.setVisibility(View.VISIBLE);
            }
            if (userEmail != null && tvDEmail != null) tvDEmail.setText(userEmail);
            if (ivDPhoto != null) {
                ProfileAvatarHelper.load(ivDPhoto, userPhotoUrl);
            }
        }

        TextView tvWeather = findViewById(R.id.tvHeaderWeather);
        if (tvWeather != null) tvWeather.setSelected(true);

        TextView tvHeaderUserName = findViewById(R.id.tvHeaderUserName);
        if (tvHeaderUserName != null) {
            String displayName = ProfileHelper.fullName(userFirstName, userLastName, userName);
            if (!displayName.isEmpty()) {
                tvHeaderUserName.setText("👤 " + displayName);
            }
        }
    }

    private void setupDrawerHeader(NavigationView navView) {
        if (navView == null) return;
        View headerView = navView.getHeaderView(0);
        if (headerView == null) return;

        View btnLogout = headerView.findViewById(R.id.btnPopupSignOut);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawers();
                performLogout();
            });
        }

        View btnPhoto = headerView.findViewById(R.id.btnChangePhoto);
        if (btnPhoto != null) {
            btnPhoto.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawers();
                startActivity(new Intent(this, UpdateProfile.class));
            });
        }
    }

    private void maybeRequireLocation(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (locationGateShown || doc == null || !doc.exists()) return;
        if (hasCompleteLocation(doc)) return;
        locationGateShown = true;
        Intent intent = new Intent(this, UpdateProfile.class);
        intent.putExtra(UpdateProfile.EXTRA_REQUIRE_LOCATION, true);
        startActivity(intent);
    }

    private static boolean hasCompleteLocation(com.google.firebase.firestore.DocumentSnapshot doc) {
        return !isBlank(doc.getString("region"))
                && !isBlank(doc.getString("district"))
                && !isBlank(doc.getString("ward"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void showProfilePopup(View anchor) {
        View popupView = getLayoutInflater().inflate(R.layout.layout_profile_popup, null);
        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(popupView, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);

        // Set User Info in Popup
        TextView tvName = popupView.findViewById(R.id.tvPopupName);
        TextView tvEmail = popupView.findViewById(R.id.tvPopupEmail);
        ImageView ivPhoto = popupView.findViewById(R.id.ivPopupProfilePhoto);

        String displayName = ProfileHelper.fullName(userFirstName, userLastName, userName);
        tvName.setText(!displayName.isEmpty() ? displayName : getString(R.string.profile_name_placeholder));
        tvEmail.setText(ProfileHelper.roleLabel(this, userRole));
        if (userPhotoUrl != null && !userPhotoUrl.isEmpty()) {
            Glide.with(this).load(userPhotoUrl).circleCrop().into(ivPhoto);
        }

        popupView.findViewById(R.id.btnPopupUploadPhoto).setOnClickListener(v -> {
            popupWindow.dismiss();
            startActivity(new Intent(this, UpdateProfile.class));
        });

        popupView.findViewById(R.id.btnPopupSignOut).setOnClickListener(v -> {
            popupWindow.dismiss();
            performLogout();
        });

        popupWindow.setElevation(20);
        popupWindow.showAsDropDown(anchor, -250, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationBadge();
        loadUserInfo();
        startScanStatsListener();
        weatherHelper.refresh();
        locationGateShown = false;
    }

    @Override
    protected void onStop() {
        stopScanStatsListener();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        weatherHelper.detach();
        greetingHelper.cancel();
        stopScanStatsListener();
        super.onDestroy();
    }

    private void updateNotificationBadge() {
        SharedPreferences p = getSharedPreferences(PREFS_NOTIFICATION, MODE_PRIVATE);
        int c = p.getInt(KEY_COUNT, 0);
        if (tvNotificationCount != null) {
            tvNotificationCount.setVisibility(c > 0 ? View.VISIBLE : View.GONE);
            tvNotificationCount.setText(String.valueOf(c));
        }
    }

    public static void incrementNotificationCount(Context context, String title, String msg) {
        incrementScanNotification(context, title, msg, null, null, null, null, null);
    }

    public static void incrementScanNotification(Context context, String title, String msg,
                                                 String scanId, String imageUrl, String imagePath,
                                                 String diseaseName, String confidence) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NOTIFICATION, Context.MODE_PRIVATE);
        int n = p.getInt(KEY_COUNT, 0) + 1;
        p.edit().putInt(KEY_COUNT, n).apply();

        NotificationDbHelper db = new NotificationDbHelper(context);
        db.insertScanNotification(title, msg, scanId, imageUrl, imagePath, diseaseName, confidence);
    }

    private void observeAdminReplies() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Query query = mDatabase.child("farmer_challenges")
                .orderByChild("userId")
                .equalTo(user.getUid());

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SharedPreferences replyPrefs = getSharedPreferences(PREFS_REPLY_STATE, MODE_PRIVATE);
                for (DataSnapshot child : snapshot.getChildren()) {
                    String challengeId = child.getKey();
                    if (challengeId == null) continue;

                    String adminReply = child.child("adminReply").getValue(String.class);
                    if (adminReply == null || adminReply.trim().isEmpty()) continue;

                    Long replyAt = child.child("adminReplyAt").getValue(Long.class);
                    if (replyAt == null) {
                        replyAt = child.child("timestamp").getValue(Long.class);
                    }
                    if (replyAt == null) continue;

                    long lastSeen = replyPrefs.getLong(challengeId, 0L);
                    if (replyAt > lastSeen) {
                        String title = child.child("title").getValue(String.class);
                        if (title == null || title.trim().isEmpty()) {
                            title = "Farmer Challenge";
                        }
                        NotificationHelper.showNotification(
                                FarmerDashboardActivity.this,
                                "Admin Reply: " + title,
                                adminReply
                        );
                        replyPrefs.edit().putLong(challengeId, replyAt).apply();
                        updateNotificationBadge();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // no-op
            }
        });
    }
}
