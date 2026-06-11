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

public class FarmerDashboardActivity extends BaseActivity {

    public static final String PREFS_NOTIFICATION = "notification_prefs";
    public static final String KEY_COUNT = "unread_count";
    private DrawerLayout drawerLayout;
    private TextView tvNotificationCount;
    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private String userName = "Farmer", userEmail = "", userPhotoUrl = "";
    private static final String PREFS_REPLY_STATE = "challenge_reply_state";

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
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, UpdateProfile.class));
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
    }

    private void loadUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        userEmail = user.getEmail();
        
        // Load from Cache First for instant feel
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userName = prefs.getString(KEY_NAME, "Farmer");
        userPhotoUrl = prefs.getString(KEY_PHOTO, "");

        updateUIWithUserInfo();

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .addSnapshotListener((doc, e) -> {
                    if (doc != null && doc.exists() && !isFinishing()) {
                        String name = doc.getString("name");
                        String photo = doc.getString("photoUrl");
                        String fName = doc.getString("firstName");
                        String lName = doc.getString("lastName");
                        if (name != null) userName = name;
                        if (photo != null) userPhotoUrl = photo;
                        saveUserCache(null, name, photo, fName, lName);
                        updateUIWithUserInfo();
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

                    // Update cache if changed
                    saveUserCache(null, name, photo, fName, lName);
                    
                    userName = name != null ? name : userName;
                    userEmail = email != null ? email : userEmail;
                    userPhotoUrl = photo != null ? photo : userPhotoUrl;

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

        if (ivHeaderProfile != null && userPhotoUrl != null && !userPhotoUrl.isEmpty()) {
            Glide.with(this).load(userPhotoUrl).circleCrop().into(ivHeaderProfile);
        }

        if (headerView != null) {
            TextView tvDName = headerView.findViewById(R.id.tvProfileName);
            TextView tvDEmail = headerView.findViewById(R.id.tvProfileEmail);
            ImageView ivDPhoto = headerView.findViewById(R.id.ivProfilePhoto);

            if (userName != null) tvDName.setText(userName);
            if (userEmail != null) tvDEmail.setText(userEmail);
            if (userPhotoUrl != null && !userPhotoUrl.isEmpty()) {
                Glide.with(this).load(userPhotoUrl).circleCrop().into(ivDPhoto);
            }
        }
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

        tvName.setText(userName);
        tvEmail.setText(userEmail);
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
